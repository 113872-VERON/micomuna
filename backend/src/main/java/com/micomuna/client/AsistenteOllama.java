package com.micomuna.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.PedidoIa.FotoIa;
import com.micomuna.models.ResultadoIa;

// Proveedor Ollama (MC-24): un modelo local con visión (por defecto qwen3-vl). POST /api/chat con las imágenes en base64
// y el esquema JSON en "format" (salida estructurada). Misma validación que Claude. No reintenta: es un servidor propio y
// un reintento solo duplicaría la espera.
// Tiempo: el tiempo de espera es el máximo TOTAL del análisis (conectar, enviar, esperar y leer la respuesta), no el de
// cada lectura: un servidor que gotea la respuesta de a poco no puede retener el hilo más que eso. Al vencerse, un
// cronómetro cierra la conexión y desbloquea la lectura.
// Tamaño: la respuesta se lee con un tope de bytes y se corta apenas lo pasa; nunca se la carga entera antes de medirla.
public class AsistenteOllama implements AsistenteIa, AutoCloseable {

	public static final String URL_POR_DEFECTO = "http://localhost:11434";
	public static final String MODELO_POR_DEFECTO = "qwen3-vl";
	// Tope de lo que se lee de la respuesta de Ollama (con "stream": false es una sola respuesta chica).
	public static final int RESPUESTA_MAXIMA_BYTES = 200_000;

	private static final Logger log = LoggerFactory.getLogger(AsistenteOllama.class);
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final String SIN_CONEXION = "No hubo conexión con Ollama o se agotó el tiempo de espera.";

	private final HttpClient http;
	private final URI direccion;
	private final String modelo;
	private final Duration tiempoTotal;
	private final ScheduledExecutorService cronometro = Executors.newSingleThreadScheduledExecutor(tarea -> {
		Thread hilo = new Thread(tarea, "ollama-cronometro");
		hilo.setDaemon(true);
		return hilo;
	});

	public AsistenteOllama(String url, String modelo, Duration tiempoTotal) {
		this.modelo = modelo == null || modelo.isBlank() ? MODELO_POR_DEFECTO : modelo.strip();
		String base = url == null || url.isBlank() ? URL_POR_DEFECTO : url.strip();
		if (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		this.direccion = URI.create(base + "/api/chat");
		this.tiempoTotal = tiempoTotal;
		this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(tiempoTotal)
				.followRedirects(HttpClient.Redirect.NEVER).build();
	}

	@Override
	public ProveedorIa proveedor() {
		return ProveedorIa.OLLAMA;
	}

	@Override
	public String modelo() {
		return modelo;
	}

	@Override
	public boolean disponible() {
		return true;
	}

	@Override
	public void close() {
		cronometro.shutdownNow();
	}

	@Override
	public ResultadoIa analizar(PedidoIa pedido) {
		long inicio = System.nanoTime();
		try {
			String cuerpo = JSON.writeValueAsString(armar(pedido));
			HttpRequest peticion = HttpRequest.newBuilder(direccion).timeout(tiempoTotal)
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(cuerpo, StandardCharsets.UTF_8)).build();
			HttpResponse<InputStream> respuesta = http.send(peticion, HttpResponse.BodyHandlers.ofInputStream());
			long restante = tiempoTotal.toNanos() - (System.nanoTime() - inicio);
			String texto = leer(respuesta, restante);
			if (texto == null) {
				return error("La respuesta de Ollama es demasiado larga.");
			}
			if (respuesta.statusCode() / 100 != 2) {
				return error("Ollama respondió con un error (" + respuesta.statusCode() + ").");
			}
			return interpretar(texto, pedido);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return error("Se interrumpió el análisis.");
		} catch (IOException e) {
			// Sin conexión, tiempo agotado (también el de la lectura cortada por el cronómetro) o conexión cerrada.
			return error(SIN_CONEXION);
		} catch (RuntimeException e) {
			log.warn("Ollama: error inesperado al analizar ({})", e.getClass().getSimpleName());
			return error("Error inesperado al llamar a Ollama.");
		}
	}

	// Lee el cuerpo con tope de bytes y de tiempo. Devuelve null si pasa el tope de bytes (se deja de leer enseguida).
	// Si vence el tiempo total, el cronómetro cierra el flujo y la lectura que estaba bloqueada lanza IOException.
	private String leer(HttpResponse<InputStream> respuesta, long restanteNanos) throws IOException {
		AtomicBoolean vencio = new AtomicBoolean(false);
		try (InputStream flujo = respuesta.body()) {
			ScheduledFuture<?> alarma = cronometro.schedule(() -> {
				vencio.set(true);
				try {
					flujo.close();
				} catch (IOException e) {
					// Ya estaba cerrado.
				}
			}, Math.max(restanteNanos, 1), TimeUnit.NANOSECONDS);
			try {
				ByteArrayOutputStream acumulado = new ByteArrayOutputStream();
				byte[] bloque = new byte[8192];
				int leidos;
				while ((leidos = flujo.read(bloque)) != -1) {
					if (acumulado.size() + leidos > RESPUESTA_MAXIMA_BYTES) {
						return null;
					}
					acumulado.write(bloque, 0, leidos);
				}
				if (vencio.get()) {
					throw new IOException("tiempo agotado");
				}
				return acumulado.toString(StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw vencio.get() ? new IOException("tiempo agotado") : e;
			} finally {
				alarma.cancel(false);
			}
		}
	}

	// El cuerpo de POST /api/chat. Las imágenes van en base64 dentro del mensaje del usuario.
	Map<String, Object> armar(PedidoIa pedido) {
		List<String> imagenes = new ArrayList<>();
		for (FotoIa foto : pedido.fotos()) {
			imagenes.add(Base64.getEncoder().encodeToString(foto.bytes()));
		}
		Map<String, Object> usuario = new LinkedHashMap<>();
		usuario.put("role", "user");
		usuario.put("content", ContratoIa.textoDelPedido(pedido));
		if (!imagenes.isEmpty()) {
			usuario.put("images", imagenes);
		}
		Map<String, Object> sistema = new LinkedHashMap<>();
		sistema.put("role", "system");
		sistema.put("content", ContratoIa.SISTEMA);
		Map<String, Object> opciones = new LinkedHashMap<>();
		opciones.put("temperature", 0);
		opciones.put("num_predict", ContratoIa.MAXIMO_TOKENS_DE_SALIDA);
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("model", modelo);
		cuerpo.put("stream", false);
		// Sin "pensar" en voz alta: solo se quiere el JSON de la clasificación.
		cuerpo.put("think", false);
		cuerpo.put("messages", List.of(sistema, usuario));
		cuerpo.put("format", ContratoIa.esquema(pedido.tipos()));
		cuerpo.put("options", opciones);
		return cuerpo;
	}

	private ResultadoIa interpretar(String cuerpo, PedidoIa pedido) {
		if (cuerpo.isBlank()) {
			return error("La respuesta de Ollama está vacía.");
		}
		JsonNode raiz;
		try {
			raiz = JSON.readTree(cuerpo);
		} catch (Exception e) {
			return error("La respuesta de Ollama no es un JSON válido.");
		}
		if (raiz == null || !raiz.isObject()) {
			return error("La respuesta de Ollama no tiene el formato esperado.");
		}
		Long tokensEntrada = numero(raiz.get("prompt_eval_count"));
		Long tokensSalida = numero(raiz.get("eval_count"));
		JsonNode razon = raiz.get("done_reason");
		if (razon != null && razon.isTextual() && "length".equals(razon.asText())) {
			return ContratoIa.fallida("La respuesta se cortó por largo (num_predict).", tokensEntrada, tokensSalida);
		}
		JsonNode contenido = raiz.path("message").get("content");
		String texto = contenido != null && contenido.isTextual() ? contenido.asText() : null;
		return ContratoIa.interpretar(texto, pedido, tokensEntrada, tokensSalida);
	}

	private static Long numero(JsonNode nodo) {
		return nodo != null && nodo.isIntegralNumber() && nodo.asLong() >= 0 ? nodo.asLong() : null;
	}

	private static ResultadoIa error(String detalle) {
		return ContratoIa.fallida(detalle, null, null);
	}
}
