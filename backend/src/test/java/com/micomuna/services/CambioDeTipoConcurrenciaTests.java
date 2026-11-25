package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.SugerenciaIaRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// MC-25: la carrera con la IA, de verdad contra Oracle. El análisis corre en segundo plano y solo AGREGA filas de sugerencia;
// el personal cambia el tipo con el reclamo bloqueado. Si ocurren a la vez no hay error ni espera: la sugerencia que llega
// queda pendiente de decisión. Sin @Transactional en la clase: los datos se confirman y se borran a mano al final.
@SpringBootTest
class CambioDeTipoConcurrenciaTests {

	private static final int SEGUNDOS_MAXIMOS = 30;

	@Autowired
	private CambioDeTipoService servicio;
	@Autowired
	private ComunaRepository comunas;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private ReclamoRepository reclamos;
	@Autowired
	private TipoReclamoRepository tipos;
	@Autowired
	private SugerenciaIaRepository sugerencias;
	@Autowired
	private PlatformTransactionManager transacciones;
	@Autowired
	private JdbcTemplate jdbc;

	private ExecutorService hilos;
	private Comuna comuna;
	private Usuario admin;
	private Reclamo reclamo;

	@BeforeEach
	void preparar() {
		hilos = Executors.newFixedThreadPool(2);
		Comuna c = new Comuna();
		c.setNombre("Comuna de concurrencia");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t25-conc-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		admin = usuario("admin", Rol.ADMIN_COMUNA);
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
		jdbc.update("delete from reclamo_sugerencia_ia where id_comuna = ?", id);
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

	private String tipoDelReclamo() {
		return jdbc.queryForObject("select t.codigo from reclamo r join tipo_reclamo t on t.id = r.id_tipo where r.id = ?",
				String.class, reclamo.getId());
	}

	// Un hilo es "la IA": guarda una sugerencia HECHA en su propia transacción, como AnalisisIaService.
	private Future<Boolean> iaGuarda(String tipo) {
		return hilos.submit(() -> {
			TransactionTemplate propia = new TransactionTemplate(transacciones);
			propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
			propia.executeWithoutResult(e -> sugerencias.save(SugerenciaIa.hecha(comuna.getId(), reclamo.getId(),
					ProveedorIa.CLAUDE, "m", tipo, PrioridadIa.MEDIA, "Se ve un pozo.", 1L, 1L)));
			return true;
		});
	}

	@Test
	void laSugerenciaQueLlegaMientrasElPersonalTieneElReclamoTomadoSeGuardaSinEsperarYQuedaPendiente() throws Exception {
		CountDownLatch personalTomoElReclamo = new CountDownLatch(1);
		CountDownLatch iaTermino = new CountDownLatch(1);

		Future<Boolean> personal = hilos.submit(() -> {
			SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
					new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
			try {
				return new TransactionTemplate(transacciones).execute(estado -> {
					servicio.cambiarTipo(1, "LUZ", admin.getId());
					personalTomoElReclamo.countDown();
					try {
						// La transacción del personal sigue abierta (con el reclamo bloqueado) hasta que la IA termina.
						return iaTermino.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException(e);
					}
				});
			} finally {
				SecurityContextHolder.clearContext();
			}
		});
		assertThat(personalTomoElReclamo.await(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();

		// La IA no espera el bloqueo del reclamo: solo agrega una fila.
		assertThat(iaGuarda("BACHE").get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();
		iaTermino.countDown();
		assertThat(personal.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS)).isTrue();

		assertThat(tipoDelReclamo()).isEqualTo("LUZ");
		// La fila de la IA quedó sin decisión: el personal decide cuando la ve.
		assertThat(jdbc.queryForObject("select count(*) from reclamo_sugerencia_ia where id_reclamo = ? and decision is null",
				Integer.class, reclamo.getId())).isEqualTo(1);
	}

	@Test
	void dosDecisionesALaVezSeHacenDeAUnaYNoDejanDatosIncoherentes() throws Exception {
		new TransactionTemplate(transacciones).executeWithoutResult(e -> sugerencias.save(SugerenciaIa.hecha(comuna.getId(),
				reclamo.getId(), ProveedorIa.CLAUDE, "m", "LUZ", PrioridadIa.MEDIA, "Se ve una luz.", 1L, 1L)));

		Future<String> a = hilos.submit(() -> decidir("LUZ"));
		Future<String> b = hilos.submit(() -> decidir("BACHE"));
		String resA = a.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);
		String resB = b.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS);

		// Cada una se hizo completa o falló limpio (409 MISMO_TIPO o CONFLICTO): nunca un error inesperado.
		for (String r : List.of(resA, resB)) {
			assertThat(r).matches("OK|MismoTipoException|PessimisticLockingFailureException|CannotAcquireLockException");
		}
		// El reclamo y la decisión quedaron de acuerdo (la base además exige decisión y tipo coherentes).
		assertThat(jdbc.queryForObject("select tipo_decidido from reclamo_sugerencia_ia where id_reclamo = ?", String.class,
				reclamo.getId())).isEqualTo(tipoDelReclamo());
	}

	private String decidir(String tipo) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
		try {
			servicio.cambiarTipo(1, tipo, admin.getId());
			return "OK";
		} catch (RuntimeException e) {
			return e.getClass().getSimpleName();
		} finally {
			SecurityContextHolder.clearContext();
		}
	}
}
