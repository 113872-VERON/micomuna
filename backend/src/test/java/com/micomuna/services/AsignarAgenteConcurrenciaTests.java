package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// MC-19: concurrencia real de CambioDeEstadoService.asignarAgente contra Oracle, con hilos y transacciones de verdad.
// A propósito SIN @Transactional en la clase: los datos se confirman y se borran a mano al final.
// Los hilos se coordinan con latches y todo tiene tiempo máximo.
@SpringBootTest
class AsignarAgenteConcurrenciaTests {

	private static final int SEGUNDOS_MAXIMOS = 30;

	@Autowired
	private CambioDeEstadoService servicio;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ReclamoRepository reclamos;

	@Autowired
	private TipoReclamoRepository tipos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private ExecutorService hilos;
	private Comuna comuna;
	private Usuario admin;
	private Usuario agente1;
	private Usuario agente2;
	private Reclamo reclamo;

	@BeforeEach
	void preparar() {
		hilos = Executors.newFixedThreadPool(3);
		Comuna c = new Comuna();
		c.setNombre("Comuna de concurrencia");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t19-conc-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		admin = usuario("admin", Rol.ADMIN_COMUNA);
		agente1 = usuario("agente1", Rol.AGENTE);
		agente2 = usuario("agente2", Rol.AGENTE);
		Usuario vecino = usuario("vecino", Rol.VECINO);

		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(1);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Un pozo");
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setEstado(EstadoReclamo.RECIBIDO);
		reclamo = reclamos.saveAndFlush(r);
	}

	@AfterEach
	void limpiar() {
		hilos.shutdownNow();
		Long id = comuna.getId();
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo where id_comuna = ?", id);
		jdbc.update("delete from usuario where id_comuna = ?", id);
		jdbc.update("delete from comuna where id = ?", id);
	}

	private Usuario usuario(String nombre, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(nombre + "-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// El SecurityContext es por hilo: cada hilo entra como el admin de la comuna.
	private <T> Future<T> correr(Callable<T> tarea) {
		return hilos.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
					new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
			try {
				return tarea.call();
			} finally {
				SecurityContextHolder.clearContext();
			}
		});
	}

	private void asignarA(Usuario agente) {
		servicio.asignarAgente(reclamo.getNumero(), agente.getId(), admin.getId());
	}

	private String estadoEnBase() {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, reclamo.getId());
	}

	private Long agenteEnBase() {
		return jdbc.queryForObject("select id_agente from reclamo where id = ?", Long.class, reclamo.getId());
	}

	private List<String> historialEnBase() {
		return jdbc.queryForList(
				"select nvl(estado_anterior, '-') || '>' || estado || '@' || nvl(to_char(id_agente), '-') from reclamo_estado where id_reclamo = ? order by id",
				String.class, reclamo.getId());
	}

	// Devuelve la causa del fallo de una tarea, o null si terminó bien.
	private static Throwable fallo(Future<?> tarea) throws Exception {
		try {
			tarea.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			return null;
		} catch (ExecutionException e) {
			return e.getCause();
		}
	}

	// 1) A tiene el reclamo tomado (transacción abierta) y B intenta asignarlo a otro agente: B espera los 3 segundos del
	// bloqueo y falla con la excepción que ApiExceptionHandler responde como 409 CONFLICTO. Nada de B queda escrito.
	@Test
	void mientrasUnaAsignacionTieneElReclamoOtraEsperaYFallaConConflicto() throws Exception {
		CountDownLatch aTomoElBloqueo = new CountDownLatch(1);
		CountDownLatch aPuedeConfirmar = new CountDownLatch(1);

		Future<Boolean> a = correr(() -> new TransactionTemplate(transacciones).execute(estado -> {
			asignarA(agente1);
			aTomoElBloqueo.countDown();
			try {
				return aPuedeConfirmar.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}));
		assertThat(aTomoElBloqueo.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).as("A tomó el bloqueo").isTrue();

		Future<Boolean> b = correr(() -> {
			asignarA(agente2);
			return true;
		});
		Throwable falloDeB = fallo(b);
		aPuedeConfirmar.countDown();

		assertThat(a.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		assertThat(falloDeB).as("B tiene que fallar por el bloqueo").isInstanceOf(PessimisticLockingFailureException.class);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		assertThat(agenteEnBase()).isEqualTo(agente1.getId());
		assertThat(historialEnBase()).containsExactly("RECIBIDO>ASIGNADO@" + agente1.getId());
	}

	// 2) Dos asignaciones a la vez, de verdad, a agentes distintos: o se serializan (la segunda reasigna) o la segunda
	// vence el bloqueo (409 CONFLICTO). En cualquier caso hay una fila de historial por cada una que tuvo éxito (ni de más
	// ni de menos), y el reclamo queda a cargo del agente de la última fila.
	@Test
	void dosAsignacionesSimultaneasQuedanSerializadasOConConflictoSinFilasDeMas() throws Exception {
		CountDownLatch salida = new CountDownLatch(1);
		Future<Boolean> a = correr(() -> {
			salida.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			asignarA(agente1);
			return true;
		});
		Future<Boolean> b = correr(() -> {
			salida.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			asignarA(agente2);
			return true;
		});
		salida.countDown();
		Throwable falloA = fallo(a);
		Throwable falloB = fallo(b);

		int exitos = (falloA == null ? 1 : 0) + (falloB == null ? 1 : 0);
		assertThat(exitos).as("al menos una gana").isGreaterThanOrEqualTo(1);
		for (Throwable f : new Throwable[] { falloA, falloB }) {
			if (f != null) {
				assertThat(f).isInstanceOf(PessimisticLockingFailureException.class);
			}
		}
		List<String> historial = historialEnBase();
		assertThat(historial).hasSize(exitos);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		String ultima = historial.get(historial.size() - 1);
		assertThat(ultima).endsWith("@" + agenteEnBase());
		if (exitos == 2) {
			// Serializadas: la primera asignó desde RECIBIDO y la segunda reasignó desde ASIGNADO.
			assertThat(historial.get(0)).startsWith("RECIBIDO>ASIGNADO@");
			assertThat(historial.get(1)).startsWith("ASIGNADO>ASIGNADO@");
			assertThat(historial.get(0)).isNotEqualTo(historial.get(1));
		}
	}

	// 3) Se desactiva al agente mientras se lo está asignando: la desactivación toma la fila del agente (sin confirmar),
	// la asignación toma el reclamo y espera la fila del agente. Cuando la desactivación confirma, la asignación
	// valida sobre la fila ya desactivada y se rechaza (AGENTE_INVALIDO): no queda un reclamo asignado a un inactivo.
	@Test
	void unAgenteDesactivadoMientrasSeLoAsignaNoQuedaAsignado() throws Exception {
		CountDownLatch desactivadorTomoAlAgente = new CountDownLatch(1);
		CountDownLatch desactivadorPuedeConfirmar = new CountDownLatch(1);

		Future<Boolean> desactivador = correr(() -> new TransactionTemplate(transacciones).execute(estado -> {
			Usuario u = usuarios.bloquearPorIdYComuna(agente1.getId(), comuna.getId()).orElseThrow();
			u.setActivo(false);
			usuarios.saveAndFlush(u);
			desactivadorTomoAlAgente.countDown();
			try {
				return desactivadorPuedeConfirmar.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}));
		assertThat(desactivadorTomoAlAgente.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();

		Future<Boolean> asignacion = correr(() -> {
			asignarA(agente1);
			return true;
		});
		// La asignación llega al bloqueo del agente y espera (mucho menos que los 3 segundos de espera máxima).
		Thread.sleep(700);
		desactivadorPuedeConfirmar.countDown();

		Throwable falloDeLaAsignacion = fallo(asignacion);
		assertThat(desactivador.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		assertThat(falloDeLaAsignacion).isInstanceOf(PeticionInvalidaException.class);
		assertThat(((PeticionInvalidaException) falloDeLaAsignacion).getCodigo()).isEqualTo("AGENTE_INVALIDO");
		assertThat(estadoEnBase()).isEqualTo("RECIBIDO");
		assertThat(agenteEnBase()).isNull();
		assertThat(historialEnBase()).isEmpty();
	}
}
