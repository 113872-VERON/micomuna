package com.micomuna.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicInvalidDataException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.UnprocessableEntityException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.PedidoIa.FotoIa;
import com.micomuna.models.ResultadoIa;

// Proveedor Claude (MC-24) con el SDK oficial de Java de Anthropic. Un solo pedido por reclamo: las fotos como bloques
// de imagen en base64 y el texto. La respuesta es un JSON con esquema fijo (output_config.format, sin prefill ni
// tool_choice forzado), con esfuerzo "low" porque es una clasificación y con pocos tokens de salida.
// Seguridad: la clave nunca se escribe en los logs; los errores se registran solo con su clase y su código HTTP (el mensaje
// de una excepción del SDK puede traer partes del pedido).
public class AsistenteClaude implements AsistenteIa, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(AsistenteClaude.class);

	private final AnthropicClient cliente;
	private final String modelo;

	// Sin clave (null o vacía) no se crea el cliente y el asistente queda no disponible: no se llama a nadie.
	// baseUrl vacío usa la dirección oficial de la API (solo las pruebas la cambian, hacia un servidor local simulado).
	public AsistenteClaude(String apiKey, String modelo, String baseUrl, Duration tiempoDeEspera, int reintentos) {
		this.modelo = modelo;
		if (apiKey == null || apiKey.isBlank()) {
			this.cliente = null;
			return;
		}
		AnthropicOkHttpClient.Builder constructor = AnthropicOkHttpClient.builder().apiKey(apiKey.strip())
				.timeout(tiempoDeEspera).maxRetries(reintentos);
		if (baseUrl != null && !baseUrl.isBlank()) {
			constructor.baseUrl(baseUrl.strip());
		}
		this.cliente = constructor.build();
	}

	@Override
	public ProveedorIa proveedor() {
		return ProveedorIa.CLAUDE;
	}

	@Override
	public String modelo() {
		return modelo;
	}

	@Override
	public boolean disponible() {
		return cliente != null;
	}

	// Spring lo llama al cerrar la aplicación (los beans AutoCloseable se cierran solos).
	@Override
	public void close() {
		if (cliente != null) {
			cliente.close();
		}
	}

	@Override
	public ResultadoIa analizar(PedidoIa pedido) {
		if (cliente == null) {
			return new ResultadoIa.Omitida();
		}
		try {
			Message respuesta = cliente.messages().create(armar(pedido));
			return interpretar(respuesta, pedido);
		} catch (RateLimitException e) {
			return error("El proveedor limitó el uso (429).");
		} catch (InternalServerException e) {
			// 500, 502, 503, 504 y 529 (sobrecargado).
			return error("El proveedor tuvo un error o está sobrecargado (" + e.statusCode() + ").");
		} catch (UnauthorizedException | PermissionDeniedException e) {
			return error("El proveedor rechazó las credenciales (" + e.statusCode() + ").");
		} catch (NotFoundException e) {
			return error("El modelo no está disponible (404).");
		} catch (BadRequestException | UnprocessableEntityException e) {
			return error("El proveedor rechazó el pedido (" + e.statusCode() + ").");
		} catch (AnthropicServiceException e) {
			return error("El proveedor respondió con un error (" + e.statusCode() + ").");
		} catch (AnthropicIoException e) {
			return error("No hubo conexión con el proveedor o se agotó el tiempo de espera.");
		} catch (AnthropicInvalidDataException e) {
			return error("La respuesta del proveedor no tiene el formato esperado.");
		} catch (AnthropicException e) {
			return error("Falló el cliente del proveedor.");
		} catch (RuntimeException e) {
			log.warn("Claude: error inesperado al analizar ({})", e.getClass().getSimpleName());
			return error("Error inesperado al llamar al proveedor.");
		}
	}

	// El pedido a la API. Las fotos primero y el texto después (así lo recomienda la guía para imágenes).
	MessageCreateParams armar(PedidoIa pedido) {
		List<ContentBlockParam> bloques = new ArrayList<>();
		for (FotoIa foto : pedido.fotos()) {
			Base64ImageSource.MediaType tipo = tipoDeImagen(foto.tipoMime());
			if (tipo == null) {
				continue;
			}
			bloques.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
					.source(Base64ImageSource.builder().data(Base64.getEncoder().encodeToString(foto.bytes()))
							.mediaType(tipo).build())
					.build()));
		}
		bloques.add(ContentBlockParam.ofText(ContratoIa.textoDelPedido(pedido)));
		JsonOutputFormat.Schema.Builder esquema = JsonOutputFormat.Schema.builder();
		for (Map.Entry<String, Object> campo : ContratoIa.esquema(pedido.tipos()).entrySet()) {
			esquema.putAdditionalProperty(campo.getKey(), JsonValue.from(campo.getValue()));
		}
		return MessageCreateParams.builder()
				.model(modelo)
				.maxTokens(ContratoIa.MAXIMO_TOKENS_DE_SALIDA)
				.system(ContratoIa.SISTEMA)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW)
						.format(JsonOutputFormat.builder().schema(esquema.build()).build()).build())
				.addUserMessageOfContentBlockParams(bloques)
				.build();
	}

	// Antes de leer el contenido se mira por qué terminó la respuesta: una negativa (refusal) o un corte por largo
	// (max_tokens) no traen un JSON completo.
	private ResultadoIa interpretar(Message respuesta, PedidoIa pedido) {
		Long tokensEntrada = respuesta.usage().inputTokens();
		Long tokensSalida = respuesta.usage().outputTokens();
		StopReason motivo = respuesta.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(motivo)) {
			return ContratoIa.fallida("El proveedor se negó a responder (refusal).", tokensEntrada, tokensSalida);
		}
		if (StopReason.MAX_TOKENS.equals(motivo)) {
			return ContratoIa.fallida("La respuesta se cortó por largo (max_tokens).", tokensEntrada, tokensSalida);
		}
		if (!StopReason.END_TURN.equals(motivo)) {
			return ContratoIa.fallida("La respuesta terminó de forma inesperada.", tokensEntrada, tokensSalida);
		}
		String texto = respuesta.content().stream().flatMap(bloque -> bloque.text().stream()).map(TextBlock::text)
				.findFirst().orElse(null);
		return ContratoIa.interpretar(texto, pedido, tokensEntrada, tokensSalida);
	}

	private static ResultadoIa error(String detalle) {
		return ContratoIa.fallida(detalle, null, null);
	}

	// Las fotos que guarda MiComuna son PNG o JPEG; los otros dos formatos de la API se aceptan por si se suman.
	private static Base64ImageSource.MediaType tipoDeImagen(String mime) {
		if (mime == null) {
			return null;
		}
		return switch (mime.toLowerCase()) {
			case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
			case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
			case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
			case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
			default -> null;
		};
	}
}
