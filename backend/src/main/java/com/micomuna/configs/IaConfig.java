package com.micomuna.configs;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.micomuna.client.AsistenteClaude;
import com.micomuna.client.AsistenteIa;
import com.micomuna.client.AsistenteNinguno;
import com.micomuna.client.AsistenteOllama;

// Elige la IA de la aplicación (MC-24) con la propiedad micomuna.ia.proveedor (variable MICOMUNA_IA_PROVEEDOR):
// ninguno (por defecto) | claude | ollama. Un valor desconocido impide arrancar (mejor que apagar la IA sin avisar).
// Si es claude y falta ANTHROPIC_API_KEY, la aplicación arranca igual, avisa una sola vez y cada reclamo queda con la
// sugerencia OMITIDA. Las variables vacías cuentan como "no definidas".
@Configuration
public class IaConfig {

	public static final String CLAUDE_MODELO_POR_DEFECTO = "claude-haiku-5-5";
	public static final int TIEMPO_DE_ESPERA_POR_DEFECTO = 20;
	public static final int REINTENTOS_POR_DEFECTO = 1;
	public static final int TOPE_DIARIO_POR_DEFECTO = 200;
	public static final int TOPE_DIARIO_TOTAL_POR_DEFECTO = 1000;

	private static final Logger log = LoggerFactory.getLogger(IaConfig.class);

	@Bean
	AsistenteIa asistenteIa(@Value("${micomuna.ia.proveedor:ninguno}") String proveedor,
			@Value("${micomuna.ia.timeout-segundos:20}") String timeoutSegundos,
			@Value("${micomuna.ia.reintentos:1}") String reintentos,
			@Value("${micomuna.ia.claude.api-key:}") String claveDeClaude,
			@Value("${micomuna.ia.claude.modelo:}") String modeloDeClaude,
			@Value("${micomuna.ia.claude.url:}") String urlDeClaude,
			@Value("${micomuna.ia.ollama.url:}") String urlDeOllama,
			@Value("${micomuna.ia.ollama.modelo:}") String modeloDeOllama) {
		String elegido = proveedor == null || proveedor.isBlank() ? "ninguno" : proveedor.strip().toLowerCase(Locale.ROOT);
		Duration espera = Duration.ofSeconds(entero(timeoutSegundos, TIEMPO_DE_ESPERA_POR_DEFECTO, 1));
		switch (elegido) {
			case "ninguno":
				log.info("IA apagada (micomuna.ia.proveedor=ninguno): los reclamos se guardan sin sugerencia.");
				return new AsistenteNinguno();
			case "claude":
				validarUrlDeClaude(urlDeClaude);
				AsistenteClaude claude = new AsistenteClaude(claveDeClaude,
						modeloDeClaude.isBlank() ? CLAUDE_MODELO_POR_DEFECTO : modeloDeClaude.strip(), urlDeClaude, espera,
						entero(reintentos, REINTENTOS_POR_DEFECTO, 0));
				if (!claude.disponible()) {
					log.warn("micomuna.ia.proveedor=claude pero falta ANTHROPIC_API_KEY: la IA queda apagada y los "
							+ "reclamos se guardan sin sugerencia.");
					return new AsistenteNinguno();
				}
				log.info("IA con Claude (modelo {}).", claude.modelo());
				return claude;
			case "ollama":
				validarUrlDeOllama(urlDeOllama);
				AsistenteOllama ollama = new AsistenteOllama(urlDeOllama, modeloDeOllama, espera);
				log.info("IA con Ollama (modelo {}).", ollama.modelo());
				return ollama;
			default:
				throw new IllegalStateException(
						"micomuna.ia.proveedor tiene que ser ninguno, claude u ollama (valor recibido: " + elegido + ").");
		}
	}

	// La URL alternativa de Claude (MICOMUNA_IA_CLAUDE_URL) es solo para pruebas: si viene definida tiene que ser https,
	// porque por ahí viajaría la clave. En producción no se define.
	public static void validarUrlDeClaude(String url) {
		if (url == null || url.isBlank()) {
			return;
		}
		URI uri = leer(url, "micomuna.ia.claude.url (MICOMUNA_IA_CLAUDE_URL)");
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalStateException("micomuna.ia.claude.url (MICOMUNA_IA_CLAUDE_URL) tiene que empezar con https:// "
					+ "(es solo para pruebas y en producción no se define).");
		}
	}

	// Ollama sin cifrar (http) solo se acepta en la misma máquina (localhost, 127.0.0.1, ::1) o en una red privada
	// (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, por dirección numérica). Cualquier otra dirección tiene que ser https.
	public static void validarUrlDeOllama(String url) {
		if (url == null || url.isBlank()) {
			return;
		}
		URI uri = leer(url, "micomuna.ia.ollama.url (OLLAMA_URL)");
		if ("https".equalsIgnoreCase(uri.getScheme())) {
			return;
		}
		if ("http".equalsIgnoreCase(uri.getScheme()) && esLocalOPrivado(uri.getHost())) {
			return;
		}
		throw new IllegalStateException("micomuna.ia.ollama.url (OLLAMA_URL) tiene que ser https, salvo que apunte a "
				+ "localhost, 127.0.0.1, ::1 o una dirección privada (10.x.x.x, 172.16-31.x.x, 192.168.x.x).");
	}

	private static URI leer(String url, String propiedad) {
		try {
			URI uri = new URI(url.strip());
			if (uri.getScheme() == null || uri.getHost() == null) {
				throw new URISyntaxException(url, "sin esquema o sin host");
			}
			return uri;
		} catch (URISyntaxException e) {
			throw new IllegalStateException(propiedad + " no es una dirección válida (por ejemplo https://servidor:11434).");
		}
	}

	// Solo direcciones escritas como número (sin consultar nombres a ningún DNS) y el nombre localhost.
	private static boolean esLocalOPrivado(String host) {
		String limpio = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
		if ("localhost".equalsIgnoreCase(limpio)) {
			return true;
		}
		boolean ipv4 = limpio.matches("\\d{1,3}(\\.\\d{1,3}){3}");
		boolean ipv6 = limpio.contains(":") && limpio.matches("[0-9a-fA-F:.]+");
		if (!ipv4 && !ipv6) {
			return false;
		}
		try {
			InetAddress direccion = InetAddress.getByName(limpio);
			return direccion.isLoopbackAddress() || (ipv4 && direccion.isSiteLocalAddress());
		} catch (UnknownHostException e) {
			return false;
		}
	}

	// Un número entero de la configuración; si está vacío o no es un número válido usa el valor por defecto.
	public static int entero(String valor, int pordefecto, int minimo) {
		try {
			return Math.max(minimo, Integer.parseInt(valor.strip()));
		} catch (RuntimeException e) {
			return pordefecto;
		}
	}
}
