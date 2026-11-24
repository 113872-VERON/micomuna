package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.micomuna.client.AsistenteIa;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.ResultadoIa;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// MC-24: el análisis de la IA de verdad enganchado al alta del reclamo. A propósito SIN @Transactional (el alta se confirma
// de verdad y el análisis corre en su hilo) y con un asistente falso: no se llama a ningún proveedor real. Los datos se
// borran a mano al terminar. El tope diario de estas pruebas es 2.
@SpringBootTest(properties = "micomuna.ia.tope-diario=2")
@AutoConfigureMockMvc
class AltaConAnalisisIaTests {

	static class AsistenteFalso implements AsistenteIa {
		volatile boolean disponible = true;
		volatile Function<PedidoIa, ResultadoIa> respuesta;
		volatile JdbcTemplate jdbc;
		volatile Long comunaId;
		final List<PedidoIa> pedidos = new CopyOnWriteArrayList<>();
		// Cuántos reclamos de la comuna ya estaban confirmados en la base cuando se llamó al proveedor.
		final List<Integer> reclamosConfirmadosAlLlamar = new CopyOnWriteArrayList<>();
		final List<String> hilos = new CopyOnWriteArrayList<>();

		void reiniciar() {
			disponible = true;
			respuesta = p -> new ResultadoIa.Hecha("BACHE", PrioridadIa.ALTA, "Pozo profundo en una calle transitada.", 1500L, 60L);
			pedidos.clear();
			reclamosConfirmadosAlLlamar.clear();
			hilos.clear();
		}

		@Override
		public ProveedorIa proveedor() {
			return ProveedorIa.CLAUDE;
		}

		@Override
		public String modelo() {
			return "claude-haiku-5-5";
		}

		@Override
		public boolean disponible() {
			return disponible;
		}

		@Override
		public ResultadoIa analizar(PedidoIa pedido) {
			pedidos.add(pedido);
			hilos.add(Thread.currentThread().getName());
			reclamosConfirmadosAlLlamar
					.add(jdbc.queryForObject("select count(*) from reclamo where id_comuna = ?", Integer.class, comunaId));
			return respuesta.apply(pedido);
		}
	}

	@TestConfiguration
	static class Asistentes {
		@Bean
		@Primary
		AsistenteFalso asistenteFalso() {
			return new AsistenteFalso();
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AsistenteFalso asistente;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	@Autowired
	private JdbcTemplate jdbc;

	private Comuna comuna;
	private Usuario vecino;

	@BeforeEach
	void preparar() {
		asistente.reiniciar();
		asistente.jdbc = jdbc;
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba IA");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t24-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		asistente.comunaId = comuna.getId();
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo("vecino-" + UUID.randomUUID().toString().substring(0, 6) + "@mail.com");
		u.setNombre("Vecino");
		u.setRol(Rol.VECINO);
		u.setClaveHash("hash");
		u.setActivo(true);
		vecino = usuarios.saveAndFlush(u);
	}

	@AfterEach
	void limpiar() throws Exception {
		// Deja terminar los análisis en vuelo antes de borrar nada.
		Thread.sleep(300);
		Long id = comuna.getId();
		jdbc.update("delete from reclamo_sugerencia_ia where id_comuna = ?", id);
		jdbc.update("delete from reclamo_foto where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo where id_comuna = ?", id);
		jdbc.update("delete from usuario where id_comuna = ?", id);
		jdbc.update("delete from comuna where id = ?", id);
	}

	// ---- Ayudas

	private ResultActions reportar(String tipo, String descripcion, MockMultipartFile... fotos) throws Exception {
		MockMultipartHttpServletRequestBuilder pedido = multipart("/api/reclamos");
		for (MockMultipartFile foto : fotos) {
			pedido.file(foto);
		}
		return mockMvc.perform(pedido.param("tipo", tipo).param("descripcion", descripcion).param("latitud", "-31.42")
				.param("longitud", "-64.18").header("Authorization", "Bearer " + jwt.generar(vecino)));
	}

	private MockMultipartFile foto() {
		return new MockMultipartFile("fotos", "bache.jpg", "image/jpeg", ImagenesDePrueba.jpeg(300, 200));
	}

	private List<Map<String, Object>> filas() {
		return jdbc.queryForList("""
				select estado, proveedor, modelo, tipo_sugerido, prioridad_sugerida, justificacion, detalle_error,
					tokens_entrada, tokens_salida
				from reclamo_sugerencia_ia where id_comuna = ? order by id
				""", comuna.getId());
	}

	// El análisis corre en otro hilo: se espera (como mucho 5 s) a que aparezcan las filas.
	private List<Map<String, Object>> esperarFilas(int cantidad) throws Exception {
		long limite = System.currentTimeMillis() + 5000;
		while (filas().size() < cantidad && System.currentTimeMillis() < limite) {
			Thread.sleep(25);
		}
		List<Map<String, Object>> filas = filas();
		assertThat(filas).hasSize(cantidad);
		return filas;
	}

	// ---- Una fila por reclamo, después del commit

	@Test
	void elAltaCreaUnaFilaHechaDespuesDelCommitYElProveedorRecibeLaFotoYElTexto() throws Exception {
		reportar("BACHE", "Pozo grande frente a la escuela", foto()).andExpect(status().isCreated());

		Map<String, Object> fila = esperarFilas(1).get(0);
		assertThat(fila).containsEntry("ESTADO", "HECHA").containsEntry("PROVEEDOR", "CLAUDE")
				.containsEntry("MODELO", "claude-haiku-5-5").containsEntry("TIPO_SUGERIDO", "BACHE")
				.containsEntry("PRIORIDAD_SUGERIDA", "ALTA").containsEntry("JUSTIFICACION", "Pozo profundo en una calle transitada.");
		assertThat(((Number) fila.get("TOKENS_ENTRADA")).longValue()).isEqualTo(1500L);
		assertThat(((Number) fila.get("TOKENS_SALIDA")).longValue()).isEqualTo(60L);
		assertThat(fila.get("DETALLE_ERROR")).isNull();

		assertThat(asistente.pedidos).hasSize(1);
		PedidoIa pedido = asistente.pedidos.get(0);
		assertThat(pedido.tipoElegido()).isEqualTo("BACHE");
		assertThat(pedido.descripcion()).isEqualTo("Pozo grande frente a la escuela");
		assertThat(pedido.fotos()).hasSize(1);
		assertThat(pedido.fotos().get(0).tipoMime()).isEqualTo("image/jpeg");
		assertThat(pedido.tipos()).extracting(t -> t.codigo()).containsExactlyInAnyOrder("BACHE", "LUZ", "BASURA", "ARBOL", "AGUA", "OTRO");
		// Cuando se llamó al proveedor, el reclamo ya estaba confirmado en la base, y en un hilo del ejecutor de la IA.
		assertThat(asistente.reclamosConfirmadosAlLlamar).containsExactly(1);
		assertThat(asistente.hilos.get(0)).startsWith("ia-");
		// El reclamo no cambió por lo que sugirió la IA.
		assertThat(jdbc.queryForObject("select tipo_reclamo.codigo from reclamo join tipo_reclamo on tipo_reclamo.id = reclamo.id_tipo "
				+ "where reclamo.id_comuna = ?", String.class, comuna.getId())).isEqualTo("BACHE");
	}

	@Test
	void unaFilaPorReclamoYSinFotosTambienSeAnaliza() throws Exception {
		reportar("LUZ", "Farol apagado").andExpect(status().isCreated());
		reportar("BASURA", "Basura en la plaza").andExpect(status().isCreated());

		assertThat(esperarFilas(2)).extracting(f -> f.get("ESTADO")).containsOnly("HECHA");
		assertThat(asistente.pedidos).extracting(PedidoIa::fotos).allMatch(List::isEmpty);
	}

	@Test
	void siElAltaSeRechazaNoSeAnalizaNada() throws Exception {
		reportar("NO_EXISTE", "Algo").andExpect(status().isBadRequest());

		Thread.sleep(500);
		assertThat(filas()).isEmpty();
		assertThat(asistente.pedidos).isEmpty();
	}

	// ---- El alta nunca falla por la IA

	@Test
	void siElProveedorLanzaElAltaIgualSeGuardaYQuedaUnError() throws Exception {
		asistente.respuesta = p -> {
			throw new IllegalStateException("el proveedor explotó con: " + p.descripcion());
		};

		reportar("BACHE", "Descripción privada del vecino").andExpect(status().isCreated()).andExpect(jsonPath("$.numero").value(1));

		Map<String, Object> fila = esperarFilas(1).get(0);
		assertThat(fila).containsEntry("ESTADO", "ERROR");
		assertThat((String) fila.get("DETALLE_ERROR")).contains("IllegalStateException").doesNotContain("privada");
		assertThat(fila.get("TIPO_SUGERIDO")).isNull();
		assertThat(jdbc.queryForObject("select count(*) from reclamo where id_comuna = ?", Integer.class, comuna.getId())).isEqualTo(1);
	}

	@Test
	void siElProveedorTardaMuchoElAltaResponderapidoYLaFilaApareceDespues() throws Exception {
		CountDownLatch soltar = new CountDownLatch(1);
		asistente.respuesta = p -> {
			try {
				soltar.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return new ResultadoIa.Hecha("LUZ", PrioridadIa.MEDIA, "Respondió tarde.", 10L, 5L);
		};

		long inicio = System.nanoTime();
		reportar("LUZ", "Farol apagado").andExpect(status().isCreated());
		long milisegundos = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);

		// El alta no esperó: el proveedor todavía está colgado y no hay ninguna fila.
		assertThat(filas()).isEmpty();
		assertThat(milisegundos).isLessThan(5000);
		soltar.countDown();
		assertThat(esperarFilas(1).get(0)).containsEntry("ESTADO", "HECHA").containsEntry("JUSTIFICACION", "Respondió tarde.");
	}

	@Test
	void siElProveedorDevuelveBasuraQuedaUnErrorYNingunaSugerencia() throws Exception {
		asistente.respuesta = p -> new ResultadoIa.Hecha("INVENTADO", PrioridadIa.ALTA, "x".repeat(400), 10L, 5L);

		reportar("BACHE", "Pozo").andExpect(status().isCreated());

		Map<String, Object> fila = esperarFilas(1).get(0);
		assertThat(fila).containsEntry("ESTADO", "ERROR");
		assertThat(fila.get("TIPO_SUGERIDO")).isNull();
		assertThat(fila.get("PRIORIDAD_SUGERIDA")).isNull();
		assertThat(fila.get("JUSTIFICACION")).isNull();
	}

	@Test
	void unaFallaTipificadaDelProveedorSeGuardaComoErrorConSusTokens() throws Exception {
		asistente.respuesta = p -> new ResultadoIa.Fallida("El proveedor limitó el uso (429).", 100L, null);

		reportar("BACHE", "Pozo").andExpect(status().isCreated());

		Map<String, Object> fila = esperarFilas(1).get(0);
		assertThat(fila).containsEntry("ESTADO", "ERROR").containsEntry("DETALLE_ERROR", "El proveedor limitó el uso (429).");
		assertThat(((Number) fila.get("TOKENS_ENTRADA")).longValue()).isEqualTo(100L);
		assertThat(fila.get("TOKENS_SALIDA")).isNull();
	}

	// ---- OMITIDA

	@Test
	void sinProveedorDisponibleQuedaOmitidaYNoSeLlamaANadie() throws Exception {
		asistente.disponible = false;

		reportar("BACHE", "Pozo").andExpect(status().isCreated());

		Map<String, Object> fila = esperarFilas(1).get(0);
		assertThat(fila).containsEntry("ESTADO", "OMITIDA").containsEntry("PROVEEDOR", "NINGUNO");
		assertThat(fila.get("MODELO")).isNull();
		assertThat(asistente.pedidos).isEmpty();
	}

	@Test
	void elTopeDiarioDeLaComunaOmiteLosSiguientesSinLlamarAlProveedor() throws Exception {
		reportar("BACHE", "Uno").andExpect(status().isCreated());
		esperarFilas(1);
		reportar("BACHE", "Dos").andExpect(status().isCreated());
		esperarFilas(2);

		reportar("BACHE", "Tres").andExpect(status().isCreated());

		List<Map<String, Object>> filas = esperarFilas(3);
		assertThat(filas).extracting(f -> f.get("ESTADO")).containsExactly("HECHA", "HECHA", "OMITIDA");
		assertThat(filas.get(2)).containsEntry("PROVEEDOR", "NINGUNO");
		assertThat(asistente.pedidos).hasSize(2);
	}

	@Test
	void losAnalisisDeAyerYLasOmitidasNoCuentanParaElTopeDeHoy() throws Exception {
		reportar("BACHE", "Uno").andExpect(status().isCreated());
		reportar("BACHE", "Dos").andExpect(status().isCreated());
		esperarFilas(2);
		// Una de las dos pasa a ser de hace dos días y la otra a ser una omitida: hoy no cuenta ninguna.
		jdbc.update("update reclamo_sugerencia_ia set creada_en = creada_en - interval '2' day where id = "
				+ "(select min(id) from reclamo_sugerencia_ia where id_comuna = ?)", comuna.getId());
		jdbc.update("update reclamo_sugerencia_ia set estado = 'OMITIDA', proveedor = 'NINGUNO', modelo = null, "
				+ "tipo_sugerido = null, prioridad_sugerida = null, justificacion = null, tokens_entrada = null, "
				+ "tokens_salida = null where id = (select max(id) from reclamo_sugerencia_ia where id_comuna = ?)",
				comuna.getId());

		reportar("BACHE", "Tres").andExpect(status().isCreated());

		List<Map<String, Object>> filas = esperarFilas(3);
		assertThat(filas.get(2)).containsEntry("ESTADO", "HECHA");
		assertThat(asistente.pedidos).hasSize(3);
	}

}
