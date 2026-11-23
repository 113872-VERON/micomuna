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
import org.springframework.mock.web.MockMultipartFile;
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
import com.micomuna.dtos.ResolverReclamoForm;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// MC-20: concurrencia real de AgenteReclamosService.resolver contra Oracle, con hilos y transacciones de verdad.
// A propósito SIN @Transactional en la clase: los datos se confirman y se borran a mano al final.
// Los hilos se coordinan con latches y todo tiene tiempo máximo.
@SpringBootTest
class AgenteReclamosConcurrenciaTests {

	private static final int SEGUNDOS_MAXIMOS = 30;

	@Autowired
	private CambioDeEstadoService servicio;

	@Autowired
	private AgenteReclamosService agenteReclamos;

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
		c.setSlug("t20-conc-" + UUID.randomUUID().toString().substring(0, 8));
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
		// Queda EN_REPARACION con agente1, confirmado en la base (la prueba no es transaccional).
		comoAdmin(() -> {
			servicio.cambiar(1, CambioDeEstado.asignar(agente1.getId()), admin.getId());
			servicio.cambiar(1, CambioDeEstado.a(EstadoReclamo.EN_REPARACION), admin.getId());
			return null;
		});
	}

	@AfterEach
	void limpiar() {
		hilos.shutdownNow();
		Long id = comuna.getId();
		jdbc.update("delete from reclamo_foto where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo_foto where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
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


	// En el hilo que llama, como el admin de la comuna (para armar el estado de partida).
	private <T> T comoAdmin(Callable<T> tarea) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
		try {
			return tarea.call();
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException(e);
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	// El agente1 resuelve (con foto) en su propio hilo, entrando como él.
	private Future<Boolean> resolverComoAgente1() {
		return hilos.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
					new UsuarioAutenticado(agente1.getId(), Rol.AGENTE, comuna.getId()), null, List.of()));
			try {
				MockMultipartFile foto = new MockMultipartFile("foto", "t.png", "image/png", fotoPng());
				agenteReclamos.resolver(1, new ResolverReclamoForm("Listo", foto),
						new UsuarioAutenticado(agente1.getId(), Rol.AGENTE, comuna.getId()));
				return true;
			} finally {
				SecurityContextHolder.clearContext();
			}
		});
	}

	private static byte[] fotoPng() throws Exception {
		java.awt.image.BufferedImage imagen = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
		javax.imageio.ImageIO.write(imagen, "png", salida);
		return salida.toByteArray();
	}

	private int fotosDeTrabajoEnBase() {
		return jdbc.queryForObject("select count(*) from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'",
				Integer.class, reclamo.getId());
	}

	// 1) Mientras el personal tiene el reclamo tomado (reasignándolo a otro agente, sin confirmar), el agente intenta
	// resolver: espera los 3 segundos del bloqueo y falla con la excepción que ApiExceptionHandler responde como 409
	// CONFLICTO. No queda ni estado nuevo ni foto.
	@Test
	void resolverMientrasElPersonalTieneElReclamoTomadoFallaConConflictoYNoDejaNada() throws Exception {
		CountDownLatch tomado = new CountDownLatch(1);
		CountDownLatch puedeConfirmar = new CountDownLatch(1);
		Future<Boolean> personal = correr(() -> new TransactionTemplate(transacciones).execute(estado -> {
			servicio.asignarAgente(1, agente2.getId(), admin.getId());
			tomado.countDown();
			try {
				return puedeConfirmar.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}));
		assertThat(tomado.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();

		Throwable falloDelAgente = fallo(resolverComoAgente1());
		puedeConfirmar.countDown();

		assertThat(personal.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		assertThat(falloDelAgente).isInstanceOf(PessimisticLockingFailureException.class);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		assertThat(fotosDeTrabajoEnBase()).isZero();
	}

	// 2) El personal reasigna el reclamo a otro agente y confirma justo mientras el agente1 lo estaba resolviendo: la
	// revisión previa (sin bloqueo) todavía lo veía suyo, pero con la fila bloqueada se vuelve a comprobar y ya no
	// es de él: 404, sin cambiar el estado ni dejar la foto.
	@Test
	void siSeReasignaMientrasElAgenteResuelveElAgenteRecibe404YNoSeGuardaLaFoto() throws Exception {
		CountDownLatch tomado = new CountDownLatch(1);
		CountDownLatch puedeConfirmar = new CountDownLatch(1);
		Future<Boolean> personal = correr(() -> new TransactionTemplate(transacciones).execute(estado -> {
			servicio.asignarAgente(1, agente2.getId(), admin.getId());
			tomado.countDown();
			try {
				return puedeConfirmar.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}));
		assertThat(tomado.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();

		Future<Boolean> agente = resolverComoAgente1();
		// El agente llega al bloqueo y espera (mucho menos que los 3 segundos de espera máxima), y el personal confirma.
		Thread.sleep(700);
		puedeConfirmar.countDown();

		Throwable falloDelAgente = fallo(agente);
		assertThat(personal.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		assertThat(falloDelAgente).isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(estadoEnBase()).isEqualTo("ASIGNADO");
		assertThat(agenteEnBase()).isEqualTo(agente2.getId());
		assertThat(fotosDeTrabajoEnBase()).isZero();
		assertThat(historialEnBase()).containsExactly("RECIBIDO>ASIGNADO@" + agente1.getId(),
				"ASIGNADO>EN_REPARACION@-", "EN_REPARACION>ASIGNADO@" + agente2.getId());
	}
}
