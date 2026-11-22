package com.micomuna.client;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// Reverse geocoding con Nominatim (OpenStreetMap). Politica de uso que cumple este cliente:
//  - se identifica con un User-Agent propio (nombre de la app, sin datos personales). El contacto opcional
//    (nominatim.contacto) va al final del User-Agent y no en el parametro "email": asi el correo no queda en la URL,
//    que es lo que suelen registrar los servidores y los mensajes de error. Nunca se escribe en los registros;
//  - el limite de un pedido por segundo y la cache los aplica DireccionService, que es quien lo llama.
// Lo que devuelve Nominatim es un dato no confiable: solo se leen unos pocos campos de texto, se limpian y se recortan.
@Component
public class NominatimClient {

	public static final String USER_AGENT_BASE = "MiComuna/0.1 (trabajo final, UTN)";
	public static final int DIRECCION_MAXIMA = 300;
	private static final int RESPUESTA_MAXIMA = 100_000;

	private static final Logger log = LoggerFactory.getLogger(NominatimClient.class);
	private static final ObjectMapper JSON = new ObjectMapper();

	private final RestClient http;
	private final String userAgent;

	public NominatimClient(RestClient nominatimRestClient, @Value("${nominatim.contacto:}") String contacto) {
		this.http = nominatimRestClient;
		String contactoLimpio = limpiar(contacto);
		this.userAgent = contactoLimpio.isEmpty() ? USER_AGENT_BASE
				: USER_AGENT_BASE.substring(0, USER_AGENT_BASE.length() - 1) + "; " + contactoLimpio + ")";
	}

	// Vacio si no hay direccion para el punto, si el servicio falla o si no responde a tiempo. Nunca lanza.
	public Optional<String> direccionDe(BigDecimal latitud, BigDecimal longitud) {
		try {
			String cuerpo = http.get()
					.uri(u -> u.path("/reverse")
							.queryParam("format", "jsonv2")
							.queryParam("lat", latitud.toPlainString())
							.queryParam("lon", longitud.toPlainString())
							.queryParam("accept-language", "es")
							.queryParam("zoom", 18)
							.build())
					.header(HttpHeaders.USER_AGENT, userAgent)
					.retrieve()
					.body(String.class);
			return armarDireccion(cuerpo);
		} catch (Exception e) {
			// Solo la clase del error: el mensaje de las excepciones de red trae la URL con las coordenadas exactas.
			log.warn("Nominatim no devolvio una direccion para el punto aprox. ({}, {}): {}", aprox(latitud),
					aprox(longitud), e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	// Calle y numero, barrio y localidad (en vez del display_name, larguisimo). Sin nada de eso, display_name recortado.
	static Optional<String> armarDireccion(String cuerpo) throws IOException {
		if (cuerpo == null || cuerpo.isBlank() || cuerpo.length() > RESPUESTA_MAXIMA) {
			return Optional.empty();
		}
		JsonNode raiz = JSON.readTree(cuerpo);
		if (raiz == null || !raiz.isObject() || raiz.has("error")) {
			return Optional.empty();
		}
		JsonNode direccion = raiz.get("address");
		if (direccion != null && direccion.isObject()) {
			String calle = primero(direccion, "road", "pedestrian", "footway", "path", "cycleway");
			String numero = texto(direccion, "house_number");
			String barrio = primero(direccion, "neighbourhood", "suburb", "quarter", "city_district");
			String localidad = primero(direccion, "village", "town", "city", "hamlet", "municipality");
			StringBuilder armada = new StringBuilder();
			if (calle != null) {
				armada.append(calle);
				if (numero != null) {
					armada.append(' ').append(numero);
				}
			}
			unir(armada, barrio);
			unir(armada, localidad);
			if (!armada.isEmpty()) {
				return Optional.of(recortar(armada.toString()));
			}
		}
		String completo = texto(raiz, "display_name");
		return completo == null ? Optional.empty() : Optional.of(recortar(completo));
	}

	private static void unir(StringBuilder armada, String parte) {
		if (parte != null && !armada.toString().contains(parte)) {
			armada.append(armada.isEmpty() ? "" : ", ").append(parte);
		}
	}

	private static String primero(JsonNode nodo, String... campos) {
		for (String campo : campos) {
			String valor = texto(nodo, campo);
			if (valor != null) {
				return valor;
			}
		}
		return null;
	}

	// Solo texto: cualquier otro tipo de valor se ignora.
	private static String texto(JsonNode nodo, String campo) {
		JsonNode valor = nodo.get(campo);
		if (valor == null || !valor.isTextual()) {
			return null;
		}
		String limpio = limpiar(valor.asText());
		return limpio.isEmpty() ? null : limpio;
	}

	// Sin caracteres de control ni espacios de mas.
	private static String limpiar(String texto) {
		if (texto == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder(texto.length());
		texto.codePoints().forEach(c -> sb.appendCodePoint(Character.isISOControl(c) ? ' ' : c));
		return sb.toString().strip().replaceAll("\\s{2,}", " ");
	}

	private static String recortar(String texto) {
		if (texto.length() <= DIRECCION_MAXIMA) {
			return texto;
		}
		int fin = DIRECCION_MAXIMA;
		if (Character.isHighSurrogate(texto.charAt(fin - 1))) {
			fin--;
		}
		return texto.substring(0, fin).strip();
	}

	private static String aprox(BigDecimal coordenada) {
		return coordenada.setScale(2, RoundingMode.HALF_UP).toPlainString();
	}
}
