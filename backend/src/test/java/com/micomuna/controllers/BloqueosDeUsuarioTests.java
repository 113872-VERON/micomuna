package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// Bloqueos de fila reales en Oracle (MC-14): otra transacción, en otro hilo, tiene tomada la fila mientras llega
// la petición. Sin @Transactional a propósito: los bloqueos solo se ven entre transacciones confirmadas de verdad.
// Por eso los datos se guardan en la base de desarrollo y se borran al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
class BloqueosDeUsuarioTests {

	private static final String SLUG = "t14-bloqueos";
	private static final String OCUPADO = "Otra persona está cambiando estos datos. Probá de nuevo en unos segundos.";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	@Autowired
	private TransactionTemplate transaccion;

	private final ExecutorService otroHilo = Executors.newSingleThreadExecutor();

	private Comuna comuna;
	private Usuario admin;
	private Usuario otraAdmin;
	private Usuario agente;

	@BeforeEach
	void preparar() {
		borrarDatosDePrueba();
		transaccion.executeWithoutResult(estado -> {
			Comuna c = new Comuna();
			c.setNombre("Comuna de prueba");
			c.setProvincia(Provincia.CORDOBA);
			c.setSlug(SLUG);
			comuna = comunas.saveAndFlush(c);
			admin = usuario("admin@mail.com", Rol.ADMIN_COMUNA);
			otraAdmin = usuario("otra-admin@mail.com", Rol.ADMIN_COMUNA);
			agente = usuario("agente@mail.com", Rol.AGENTE);
		});
	}

	@AfterEach
	void limpiar() {
		otroHilo.shutdownNow();
		borrarDatosDePrueba();
	}

	private void borrarDatosDePrueba() {
		transaccion.executeWithoutResult(estado -> comunas.findBySlug(SLUG).ifPresent(c -> {
			usuarios.deleteAll(usuarios.findByComunaId(c.getId()));
			usuarios.flush();
			comunas.delete(c);
		}));
	}

	private Usuario usuario(String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private ResultActions modificar(Usuario u, String cuerpo) throws Exception {
		return mockMvc.perform(patch("/api/usuarios/" + u.getId()).header("Authorization", "Bearer " + jwt.generar(admin))
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	// En otro hilo: abre una transacción, hace lo pedido (que toma un bloqueo), avisa y espera a que la suelten.
	private Future<?> mantenerBloqueo(Consumer<Void> tomarBloqueo, CountDownLatch soltar) throws Exception {
		CountDownLatch tomado = new CountDownLatch(1);
		Future<?> tarea = otroHilo.submit(() -> transaccion.executeWithoutResult(estado -> {
			tomarBloqueo.accept(null);
			tomado.countDown();
			try {
				soltar.await(20, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(tomado.await(10, TimeUnit.SECONDS)).as("el otro hilo tomó el bloqueo").isTrue();
		return tarea;
	}

	@Test
	void conElUsuarioBloqueadoPorOtroCambioSeEsperaTresSegundosYResponde409() throws Exception {
		CountDownLatch soltar = new CountDownLatch(1);
		Future<?> tarea = mantenerBloqueo(v -> usuarios.bloquearPorIdYComuna(agente.getId(), comuna.getId()), soltar);
		try {
			long inicio = System.nanoTime();
			modificar(agente, "{\"rol\":\"VECINO\"}")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("CONFLICTO"))
					.andExpect(jsonPath("$.detail").value(OCUPADO));
			Duration espera = Duration.ofNanos(System.nanoTime() - inicio);
			assertThat(espera).as("esperó el bloqueo, pero no para siempre")
					.isBetween(Duration.ofMillis(2500), Duration.ofSeconds(10));
		} finally {
			soltar.countDown();
			tarea.get(20, TimeUnit.SECONDS);
		}
		assertThat(usuarios.findById(agente.getId()).orElseThrow().getRol()).isEqualTo(Rol.AGENTE);
	}

	@Test
	void conLaComunaBloqueadaQuitarUnAdministradorEspera3SegundosYResponde409() throws Exception {
		CountDownLatch soltar = new CountDownLatch(1);
		Future<?> tarea = mantenerBloqueo(v -> comunas.bloquear(comuna.getId()), soltar);
		try {
			modificar(otraAdmin, "{\"activo\":false}")
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value(OCUPADO));
		} finally {
			soltar.countDown();
			tarea.get(20, TimeUnit.SECONDS);
		}
		assertThat(usuarios.findById(otraAdmin.getId()).orElseThrow().isActivo()).isTrue();
	}

	@Test
	void conOracleElTiempoDeEsperaVencidoLlegaComoCannotAcquireLockException() throws Exception {
		CountDownLatch soltar = new CountDownLatch(1);
		Future<?> tarea = mantenerBloqueo(v -> usuarios.bloquearPorIdYComuna(agente.getId(), comuna.getId()), soltar);
		try {
			// Hibernate pide "FOR UPDATE WAIT 3"; Oracle responde ORA-00054 al vencer la espera, Hibernate lo
			// convierte en su LockTimeoutException y Spring Data la entrega como CannotAcquireLockException.
			assertThatThrownBy(() -> transaccion.executeWithoutResult(
					estado -> usuarios.bloquearPorIdYComuna(agente.getId(), comuna.getId())))
					.isInstanceOf(CannotAcquireLockException.class)
					.hasCauseInstanceOf(org.hibernate.exception.LockTimeoutException.class)
					.hasMessageContaining("for update wait 3")
					.satisfies(e -> assertThat(errorDeOracle(e)).as("código de error de Oracle").isEqualTo(54));
		} finally {
			soltar.countDown();
			tarea.get(20, TimeUnit.SECONDS);
		}
	}

	// El caso que motivó el bloqueo: un administrador desactiva a alguien mientras otro le cambia el rol.
	// Sin bloqueo, el segundo guardado partía de la fila vieja (activo) y lo volvía a activar.
	@Test
	void unCambioDeRolSimultaneoNoReactivaAQuienOtroAcabaDeDesactivar() throws Exception {
		CountDownLatch soltar = new CountDownLatch(1);
		Future<?> tarea = mantenerBloqueo(v -> {
			Usuario u = usuarios.bloquearPorIdYComuna(agente.getId(), comuna.getId()).orElseThrow();
			u.setActivo(false);
			usuarios.saveAndFlush(u);
		}, soltar);
		// La desactivación se confirma al segundo, mientras la otra petición espera el bloqueo.
		otroHiloSoltarEn(soltar, Duration.ofSeconds(1));

		modificar(agente, "{\"rol\":\"VECINO\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("VECINO"))
				.andExpect(jsonPath("$.estado").value("INACTIVO"));
		tarea.get(20, TimeUnit.SECONDS);

		Usuario guardado = usuarios.findById(agente.getId()).orElseThrow();
		assertThat(guardado.isActivo()).isFalse();
		assertThat(guardado.getRol()).isEqualTo(Rol.VECINO);
	}

	// Código de la primera SQLException de la cadena (debajo queda la excepción interna del driver de Oracle).
	private static Integer errorDeOracle(Throwable e) {
		for (Throwable actual = e; actual != null; actual = actual.getCause()) {
			if (actual instanceof SQLException sql) {
				return sql.getErrorCode();
			}
		}
		return null;
	}

	private static void otroHiloSoltarEn(CountDownLatch soltar, Duration demora) {
		Thread temporizador = new Thread(() -> {
			try {
				Thread.sleep(demora.toMillis());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			soltar.countDown();
		});
		temporizador.setDaemon(true);
		temporizador.start();
	}
}
