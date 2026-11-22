package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

// El número de seguimiento se toma con la fila de la comuna bloqueada (MC-15). Igual que BloqueosDeUsuarioTests:
// otra transacción, en otro hilo, tiene tomada la fila mientras llega el alta. Sin @Transactional a propósito:
// los bloqueos solo se ven entre transacciones de verdad. Los datos se borran al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
class BloqueoNumeroReclamoTests {

	private static final String SLUG = "t15-bloqueo-numero";
	// Mensaje propio del alta (el 409 de los bloqueos de MC-14 sigue con el suyo).
	private static final String OCUPADO = "Hay muchos reclamos entrando en este momento. Probá de nuevo en unos segundos.";

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

	@Autowired
	private JdbcTemplate jdbc;

	private final ExecutorService otroHilo = Executors.newSingleThreadExecutor();

	private Comuna comuna;
	private Usuario vecino;

	@BeforeEach
	void preparar() {
		borrarDatosDePrueba();
		transaccion.executeWithoutResult(estado -> {
			Comuna c = new Comuna();
			c.setNombre("Comuna de prueba");
			c.setProvincia(Provincia.CORDOBA);
			c.setSlug(SLUG);
			comuna = comunas.saveAndFlush(c);
			Usuario u = new Usuario();
			u.setComuna(comuna);
			u.setCorreo("vecino@mail.com");
			u.setNombre("Vecino");
			u.setRol(Rol.VECINO);
			u.setClaveHash("hash");
			u.setActivo(true);
			vecino = usuarios.saveAndFlush(u);
		});
	}

	@AfterEach
	void limpiar() {
		otroHilo.shutdownNow();
		borrarDatosDePrueba();
	}

	// El historial no se borra en cascada a propósito; las fotos sí se van con el reclamo.
	private void borrarDatosDePrueba() {
		String deLaComuna = "(select id from comuna where slug = ?)";
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = "
				+ deLaComuna + ")", SLUG);
		jdbc.update("delete from reclamo where id_comuna = " + deLaComuna, SLUG);
		jdbc.update("delete from usuario where id_comuna = " + deLaComuna, SLUG);
		jdbc.update("delete from comuna where slug = ?", SLUG);
	}

	private ResultActions reportar() throws Exception {
		return mockMvc.perform(multipart("/api/reclamos")
				.param("tipo", "LUZ")
				.param("descripcion", "El farol de la esquina está apagado")
				.param("referencia", "Esquina de la plaza")
				.header("Authorization", "Bearer " + jwt.generar(vecino)));
	}

	private int reclamosGuardados() {
		return jdbc.queryForObject("select count(*) from reclamo where id_comuna = ?", Integer.class, comuna.getId());
	}

	private int contador() {
		return jdbc.queryForObject("select ultimo_numero_reclamo from comuna where id = ?", Integer.class, comuna.getId());
	}

	@Test
	void conLaComunaBloqueadaElAltaEsperaTresSegundosYResponde409() throws Exception {
		CountDownLatch tomado = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		Future<?> tarea = otroHilo.submit(() -> transaccion.executeWithoutResult(estado -> {
			comunas.bloquear(comuna.getId());
			tomado.countDown();
			try {
				soltar.await(20, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(tomado.await(10, TimeUnit.SECONDS)).as("el otro hilo tomó el bloqueo").isTrue();
		try {
			long inicio = System.nanoTime();
			reportar()
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

		// No quedó nada a medias y el número no se gastó: el próximo reclamo es el 1.
		assertThat(reclamosGuardados()).isZero();
		assertThat(contador()).isZero();
		reportar().andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1));
		assertThat(contador()).isEqualTo(1);
	}

	// Dos altas que llegan casi juntas: la segunda espera a que la primera confirme y recibe el número siguiente.
	@Test
	void dosAltasSimultaneasRecibenNumerosDistintos() throws Exception {
		CountDownLatch tomado = new CountDownLatch(1);
		CountDownLatch soltar = new CountDownLatch(1);
		// La primera "alta" suma el contador con la fila tomada y tarda un segundo en confirmar.
		Future<?> tarea = otroHilo.submit(() -> transaccion.executeWithoutResult(estado -> {
			comunas.bloquear(comuna.getId());
			comunas.sumarNumeroReclamo(comuna.getId());
			tomado.countDown();
			try {
				soltar.await(20, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();
		Thread temporizador = new Thread(() -> {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			soltar.countDown();
		});
		temporizador.setDaemon(true);
		temporizador.start();

		reportar().andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(2));
		tarea.get(20, TimeUnit.SECONDS);
		assertThat(contador()).isEqualTo(2);
	}
}
