package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
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
import com.micomuna.exceptions.TransicionInvalidaException;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// MC-65: concurrencia real de CambioDeEstadoService.cambiar contra Oracle, con hilos y transacciones de verdad.
// A propósito SIN @Transactional en la clase: los datos se confirman y se borran a mano al final.
// Es determinista: los hilos se coordinan con latches y todo tiene tiempo máximo (no hay esperas largas a ciegas).
@SpringBootTest
class CambioDeEstadoConcurrenciaTests {

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
	private Usuario agente;
	private Reclamo reclamo;

	@BeforeEach
	void preparar() {
		hilos = Executors.newFixedThreadPool(2);
		Comuna c = new Comuna();
		c.setNombre("Comuna de concurrencia");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t65-conc-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		agente = usuario("agente", Rol.AGENTE);
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

	// El SecurityContext es por hilo: cada hilo entra como el agente de la comuna.
	private void entrarComoAgente() {
		UsuarioAutenticado principal = new UsuarioAutenticado(agente.getId(), Rol.AGENTE, comuna.getId());
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	private <T> Future<T> correr(Callable<T> tarea) {
		return hilos.submit(() -> {
			entrarComoAgente();
			try {
				return tarea.call();
			} finally {
				SecurityContextHolder.clearContext();
			}
		});
	}

	private void asignar() {
		servicio.cambiar(reclamo.getNumero(), CambioDeEstado.a(EstadoReclamo.ASIGNADO), agente.getId());
	}

	private String estadoEnBase() {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, reclamo.getId());
	}

	private List<String> historialEnBase() {
		return jdbc.queryForList("select estado_anterior || '>' || estado from reclamo_estado where id_reclamo = ? order by id",
				String.class, reclamo.getId());
	}

	private static Throwable causaDe(ExecutionException e) {
		return e.getCause();
	}

	// 1) A tiene la fila tomada (transacción abierta, sin confirmar) y B intenta cambiar el mismo reclamo:
	// B espera los 3 segundos del bloqueo y falla con la excepción que ApiExceptionHandler responde como 409 CONFLICTO.
	// Mientras tanto nada se confirmó; cuando A confirma queda una sola fila nueva y el estado coherente.
	@Test
	void mientrasUnaTransaccionTieneElReclamoOtraEsperaYFallaConConflicto() throws Exception {
		CountDownLatch aTomoElBloqueo = new CountDownLatch(1);
		CountDownLatch aPuedeConfirmar = new CountDownLatch(1);

		Future<Boolean> a = correr(() -> new TransactionTemplate(transacciones).execute(estado -> {
			asignar();
			aTomoElBloqueo.countDown();
			try {
				// Tiempo máximo por si la prueba falla: A nunca queda colgada.
				return aPuedeConfirmar.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}));
		assertThat(aTomoElBloqueo.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).as("A tomó el bloqueo").isTrue();

		Future<Boolean> b = correr(() -> {
			asignar();
			return true;
		});
		Throwable falloDeB;
		try {
			b.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			falloDeB = null;
		} catch (ExecutionException e) {
			falloDeB = causaDe(e);
		} finally {
			aPuedeConfirmar.countDown();
		}

		assertThat(a.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		assertThat(falloDeB).as("B tiene que fallar por el bloqueo").isInstanceOf(PessimisticLockingFailureException.class);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		assertThat(historialEnBase()).containsExactly("RECIBIDO>ASIGNADO");
	}

	// 1 bis) Si A confirma antes de que se venza la espera, B sigue y ve el estado nuevo: su transición se evalúa
	// sobre ASIGNADO (no sobre RECIBIDO) y se rechaza. Tampoco duplica filas.
	@Test
	void siAConfirmaAntesBVeElEstadoNuevoYSuTransicionSeRechaza() throws Exception {
		CountDownLatch aTomoElBloqueo = new CountDownLatch(1);
		CountDownLatch bEmpezo = new CountDownLatch(1);

		Future<Boolean> a = correr(() -> new TransactionTemplate(transacciones).execute(estado -> {
			asignar();
			aTomoElBloqueo.countDown();
			try {
				// Confirma apenas B arrancó: B entra a esperar el bloqueo y A lo suelta enseguida, dentro de los 3 s.
				bEmpezo.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
				Thread.sleep(300);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
			return true;
		}));
		assertThat(aTomoElBloqueo.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();

		Future<Boolean> b = correr(() -> {
			bEmpezo.countDown();
			asignar();
			return true;
		});

		assertThat(a.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		ExecutionException e = org.junit.jupiter.api.Assertions.assertThrows(ExecutionException.class,
				() -> b.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));
		assertThat(causaDe(e)).isInstanceOf(TransicionInvalidaException.class);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		assertThat(historialEnBase()).containsExactly("RECIBIDO>ASIGNADO");
	}

	// 2) Dos cambios RECIBIDO -> ASIGNADO a la vez: solo uno gana; el otro falla con 409 (CONFLICTO o
	// TRANSICION_INVALIDA). Nunca dos filas ASIGNADO.
	@Test
	void dosCambiosSimultaneosDelMismoOrigenSoloUnoGana() throws Exception {
		CountDownLatch salida = new CountDownLatch(1);
		List<Future<Boolean>> resultados = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			resultados.add(correr(() -> {
				salida.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
				asignar();
				return true;
			}));
		}
		salida.countDown();

		int exitos = 0;
		List<Throwable> fallos = new ArrayList<>();
		for (Future<Boolean> f : resultados) {
			try {
				f.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
				exitos++;
			} catch (ExecutionException e) {
				fallos.add(causaDe(e));
			}
		}

		assertThat(exitos).as("cambios que ganaron").isEqualTo(1);
		assertThat(fallos).hasSize(1);
		assertThat(fallos.get(0)).isInstanceOfAny(TransicionInvalidaException.class,
				PessimisticLockingFailureException.class);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		assertThat(historialEnBase()).containsExactly("RECIBIDO>ASIGNADO");
	}
}
