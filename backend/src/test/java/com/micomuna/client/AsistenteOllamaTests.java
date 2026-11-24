package com.micomuna.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.PedidoIa.FotoIa;
import com.micomuna.models.PedidoIa.TipoIa;
import com.micomuna.models.ResultadoIa;

// MC-24: el proveedor Ollama contra un servidor local simulado (nunca se llama a un Ollama de verdad).
class AsistenteOllamaTests {

	private static final ObjectMapper JSON = new ObjectMapper();
	private static final List<TipoIa> TIPOS = List.of(new TipoIa("BACHE", "Bache o calle rota"),
			new TipoIa("LUZ", "Luminaria apagada"), new TipoIa("BASURA", "Basura acumulada"),
			new TipoIa("ARBOL", "Árbol o poda"), new TipoIa("AGUA", "Pérdida de agua"), new TipoIa("OTRO", "Otro"));

	private ServidorSimulado servidor;

	@BeforeEach
	void arrancarServidor() {
		servidor = new ServidorSimulado();
	}

	@AfterEach
	void apagarServidor() {
		servidor.close();
	}

	private AsistenteOllama asistente(int segundos) {
		return new AsistenteOllama(servidor.url(), null, Duration.ofSeconds(segundos));
	}

	private static PedidoIa pedido() {
		return new PedidoIa("BACHE", "Hay un pozo enorme en la esquina de la plaza",
				List.of(new FotoIa(new byte[] { 1, 2, 3 }, "image/jpeg"), new FotoIa(new byte[] { 4, 5 }, "image/png")), TIPOS);
	}

	private static String chat(String contenido, String motivo, int entrada, int salida) throws Exception {
		return "{\"model\":\"qwen3-vl\",\"created_at\":\"2026-11-23T10:00:00Z\",\"message\":{\"role\":\"assistant\","
				+ "\"content\":" + JSON.writeValueAsString(contenido) + "},\"done\":true,\"done_reason\":\"" + motivo
				+ "\",\"prompt_eval_count\":" + entrada + ",\"eval_count\":" + salida + "}";
	}

	private static String sugerencia(String tipo, String prioridad, String justificacion) throws Exception {
		return JSON.writeValueAsString(Map.of("tipo", tipo, "prioridad", prioridad, "justificacion", justificacion));
	}

	private ResultadoIa.Fallida fallida(ResultadoIa resultado) {
		assertThat(resultado).isInstanceOf(ResultadoIa.Fallida.class);
		return (ResultadoIa.Fallida) resultado;
	}

	@Test
	void elPedidoVaAApiChatConElModeloLasImagenesEnBase64ElEsquemaEnFormatYSinDatosDelVecino() throws Exception {
		servidor.respuesta = chat(sugerencia("BACHE", "ALTA", "Pozo profundo."), "stop", 700, 40);

		asistente(5).analizar(pedido());

		assertThat(servidor.pedidos).hasSize(1);
		ServidorSimulado.Pedido enviado = servidor.pedidos.get(0);
		assertThat(enviado.metodo()).isEqualTo("POST");
		assertThat(enviado.ruta()).isEqualTo("/api/chat");
		JsonNode cuerpo = JSON.readTree(enviado.cuerpo());
		assertThat(cuerpo.at("/model").asText()).isEqualTo("qwen3-vl");
		assertThat(cuerpo.at("/stream").asBoolean(true)).isFalse();
		assertThat(cuerpo.at("/options/num_predict").asInt()).isEqualTo(1024);
		assertThat(cuerpo.at("/messages")).hasSize(2);
		assertThat(cuerpo.at("/messages/0/role").asText()).isEqualTo("system");
		assertThat(cuerpo.at("/messages/0/content").asText()).isEqualTo(ContratoIa.SISTEMA);
		assertThat(cuerpo.at("/messages/1/role").asText()).isEqualTo("user");
		assertThat(cuerpo.at("/messages/1/content").asText()).contains("Hay un pozo enorme en la esquina de la plaza");
		assertThat(cuerpo.at("/messages/1/images")).extracting(JsonNode::asText).containsExactly(
				Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 }), Base64.getEncoder().encodeToString(new byte[] { 4, 5 }));
		// El esquema va en "format".
		assertThat(cuerpo.at("/format/type").asText()).isEqualTo("object");
		assertThat(cuerpo.at("/format/properties/tipo/enum")).extracting(JsonNode::asText)
				.containsExactly("BACHE", "LUZ", "BASURA", "ARBOL", "AGUA", "OTRO");
		assertThat(cuerpo.at("/format/required")).hasSize(3);
		assertThat(enviado.cuerpo()).doesNotContain("correo").doesNotContain("latitud").doesNotContain("longitud");
	}

	@Test
	void sinFotosNoMandaElCampoImages() throws Exception {
		servidor.respuesta = chat(sugerencia("LUZ", "BAJA", "Luminaria apagada."), "stop", 10, 5);

		asistente(5).analizar(new PedidoIa("LUZ", "Farol apagado", List.of(), TIPOS));

		assertThat(JSON.readTree(servidor.pedidos.get(0).cuerpo()).at("/messages/1").has("images")).isFalse();
	}

	@Test
	void unaRespuestaValidaEsUnaSugerenciaConLosTokens() throws Exception {
		servidor.respuesta = chat(sugerencia("AGUA", "MEDIA", "Se ve un charco que crece."), "stop", 700, 40);

		assertThat(asistente(5).analizar(pedido()))
				.isEqualTo(new ResultadoIa.Hecha("AGUA", PrioridadIa.MEDIA, "Se ve un charco que crece.", 700L, 40L));
	}

	@Test
	void unaRespuestaCortadaPorLargoEsUnError() throws Exception {
		servidor.respuesta = chat("{\"tipo\":\"BAC", "length", 700, 1024);

		ResultadoIa.Fallida fallo = fallida(asistente(5).analizar(pedido()));

		assertThat(fallo.detalle()).contains("largo");
		assertThat(fallo.tokensSalida()).isEqualTo(1024L);
	}

	@Test
	void lasRespuestasInvalidasSonErrorSinRepetirLoQueDijoElModelo() throws Exception {
		String[][] casos = {
				{ "Claro, acá va mi análisis sobre Juan Pérez...", "JSON válido" },
				{ sugerencia("INVENTADO", "ALTA", "Tipo inexistente."), "tipo sugerido no existe" },
				{ sugerencia("BACHE", "URGENTE", "Prioridad inválida."), "prioridad" },
				{ sugerencia("BACHE", "ALTA", "z".repeat(301)), "justificación" },
				{ "{\"tipo\":\"BACHE\"}", "Faltan campos" } };
		for (String[] caso : casos) {
			servidor.respuesta = chat(caso[0], "stop", 50, 5);

			ResultadoIa.Fallida fallo = fallida(asistente(5).analizar(pedido()));

			assertThat(fallo.detalle()).as(caso[0]).contains(caso[1]).doesNotContain("Juan");
		}
	}

	@Test
	void respuestasConFormatoRotoSonError() {
		for (String cuerpo : new String[] { "esto no es JSON", "[1,2]", "{}", "{\"message\":{\"content\":7}}", "" }) {
			servidor.respuesta = cuerpo;

			assertThat(asistente(5).analizar(pedido())).as(cuerpo).isInstanceOf(ResultadoIa.Fallida.class);
		}
	}

	@Test
	void unErrorHttpEsUnaFallaConElCodigo() {
		servidor.estado = 500;
		servidor.respuesta = "{\"error\":\"model not found, Hay un pozo enorme\"}";

		ResultadoIa.Fallida fallo = fallida(asistente(5).analizar(pedido()));

		assertThat(fallo.detalle()).contains("(500)").doesNotContain("pozo");
	}

	@Test
	void sinConexionEsUnaFalla() {
		AsistenteOllama asistente = asistente(5);
		servidor.close();

		assertThat(fallida(asistente.analizar(pedido())).detalle()).contains("conexión");
	}

	@Test
	void siElServidorTardaMasQueElTiempoDeEsperaEsUnaFalla() throws Exception {
		servidor.respuesta = chat(sugerencia("BACHE", "ALTA", "Tarde."), "stop", 10, 5);
		servidor.demoraMs = 3000;

		long inicio = System.nanoTime();
		ResultadoIa.Fallida fallo = fallida(asistente(1).analizar(pedido()));
		long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

		assertThat(fallo.detalle()).contains("tiempo de espera");
		assertThat(milisegundos).isLessThan(2800);
	}

	@Test
	void porDefectoUsaQwen3VlYLaDireccionLocal() {
		AsistenteOllama porDefecto = new AsistenteOllama("  ", "", Duration.ofSeconds(5));

		assertThat(porDefecto.modelo()).isEqualTo("qwen3-vl");
		assertThat(porDefecto.proveedor()).isEqualTo(ProveedorIa.OLLAMA);
		assertThat(porDefecto.disponible()).isTrue();
		assertThat(AsistenteOllama.URL_POR_DEFECTO).isEqualTo("http://localhost:11434");
	}

	@Test
	void laDireccionConBarraFinalTambienAnda() throws Exception {
		servidor.respuesta = chat(sugerencia("OTRO", "BAJA", "Poco claro."), "stop", 1, 1);

		new AsistenteOllama(servidor.url() + "/", "otro-modelo", Duration.ofSeconds(5)).analizar(pedido());

		assertThat(servidor.pedidos.get(0).ruta()).isEqualTo("/api/chat");
		assertThat(JSON.readTree(servidor.pedidos.get(0).cuerpo()).at("/model").asText()).isEqualTo("otro-modelo");
	}

	@Test
	void elTiempoEsElMaximoTotalDelAnalisisAunqueElServidorGoteeLaRespuesta() throws Exception {
		// Un byte cada 200 ms: ninguna lectura sola pasa el tiempo de espera, pero el análisis en total sí.
		servidor.goteoMs = 200;

		long inicio = System.nanoTime();
		ResultadoIa.Fallida fallo = fallida(asistente(1).analizar(pedido()));
		long milisegundos = Duration.ofNanos(System.nanoTime() - inicio).toMillis();

		assertThat(fallo.detalle()).contains("tiempo de espera");
		assertThat(milisegundos).isLessThan(2500);
	}

	@Test
	void unaRespuestaMasGrandeQueElTopeSeCortaYEsUnError() {
		servidor.bytesDeLaRespuesta = AsistenteOllama.RESPUESTA_MAXIMA_BYTES + 1;

		ResultadoIa.Fallida fallo = fallida(asistente(5).analizar(pedido()));

		assertThat(fallo.detalle()).contains("demasiado larga");
	}

	@Test
	void unaRespuestaJustoDelTamanioMaximoSeLeeEnteraAunqueNoSeaUnJsonValido() {
		servidor.bytesDeLaRespuesta = AsistenteOllama.RESPUESTA_MAXIMA_BYTES;

		ResultadoIa.Fallida fallo = fallida(asistente(5).analizar(pedido()));

		assertThat(fallo.detalle()).contains("JSON válido");
	}
}
