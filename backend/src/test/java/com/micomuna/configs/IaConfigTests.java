package com.micomuna.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.micomuna.client.AsistenteClaude;
import com.micomuna.client.AsistenteIa;
import com.micomuna.client.AsistenteNinguno;
import com.micomuna.client.AsistenteOllama;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.ResultadoIa;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// MC-24: la elección del proveedor con micomuna.ia.proveedor. No se llama a nadie: solo se arma el asistente.
class IaConfigTests {

	private static final String CLAVE = "sk-ant-clave-inventada-para-pruebas";

	private final IaConfig config = new IaConfig();
	private Logger logger;
	private ListAppender<ILoggingEvent> registro;

	@BeforeEach
	void escucharElLog() {
		logger = (Logger) LoggerFactory.getLogger(IaConfig.class);
		registro = new ListAppender<>();
		registro.start();
		logger.addAppender(registro);
	}

	@AfterEach
	void dejarDeEscuchar() {
		logger.detachAppender(registro);
	}

	private AsistenteIa armar(String proveedor, String clave, String modeloDeClaude, String urlDeOllama, String modeloDeOllama) {
		return config.asistenteIa(proveedor, "20", "2", clave, modeloDeClaude, "", urlDeOllama, modeloDeOllama);
	}

	@Test
	void porDefectoYConValorVacioElProveedorEsNinguno() {
		for (String valor : new String[] { "ninguno", "", "   ", "NINGUNO", " Ninguno " }) {
			AsistenteIa asistente = armar(valor, "", "", "", "");

			assertThat(asistente).as(valor).isInstanceOf(AsistenteNinguno.class);
			assertThat(asistente.disponible()).isFalse();
			assertThat(asistente.proveedor()).isEqualTo(ProveedorIa.NINGUNO);
			assertThat(asistente.analizar(new PedidoIa("BACHE", "x", java.util.List.of(), java.util.List.of())))
					.isInstanceOf(ResultadoIa.Omitida.class);
		}
	}

	@Test
	void claudeConClaveUsaElModeloPorDefectoOElConfigurado() {
		AsistenteIa conDefecto = armar("claude", CLAVE, "", "", "");
		AsistenteIa configurado = armar("Claude", CLAVE, " claude-otro-modelo ", "", "");

		assertThat(conDefecto).isInstanceOf(AsistenteClaude.class);
		assertThat(conDefecto.disponible()).isTrue();
		assertThat(conDefecto.modelo()).isEqualTo("claude-haiku-5-5");
		assertThat(configurado.modelo()).isEqualTo("claude-otro-modelo");
		// Ningún mensaje del log lleva la clave.
		assertThat(registro.list).noneMatch(e -> e.getFormattedMessage().contains(CLAVE));
		((AsistenteClaude) conDefecto).close();
		((AsistenteClaude) configurado).close();
	}

	@Test
	void claudeSinClaveArrancaIgualAvisaUnaVezYQuedaApagado() {
		for (String sinClave : new String[] { "", "   " }) {
			registro.list.clear();

			AsistenteIa asistente = armar("claude", sinClave, "", "", "");

			assertThat(asistente).isInstanceOf(AsistenteNinguno.class);
			assertThat(asistente.disponible()).isFalse();
			assertThat(registro.list).filteredOn(e -> e.getLevel() == Level.WARN).hasSize(1);
			assertThat(registro.list.get(0).getFormattedMessage()).contains("ANTHROPIC_API_KEY");
		}
	}

	@Test
	void ollamaUsaLosValoresPorDefectoOLosConfigurados() {
		AsistenteIa conDefecto = armar("ollama", "", "", "", "");
		AsistenteIa configurado = armar("ollama", "", "", "https://ollama.interno:11434", "llava");

		assertThat(conDefecto).isInstanceOf(AsistenteOllama.class);
		assertThat(conDefecto.modelo()).isEqualTo("qwen3-vl");
		assertThat(conDefecto.disponible()).isTrue();
		assertThat(conDefecto.proveedor()).isEqualTo(ProveedorIa.OLLAMA);
		assertThat(configurado.modelo()).isEqualTo("llava");
	}

	@Test
	void unProveedorDesconocidoImpideArrancar() {
		assertThatThrownBy(() -> armar("gpt", "", "", "", "")).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("ninguno, claude u ollama");
	}

	@Test
	void losReintentosDeClaudePorDefectoSonUno() {
		assertThat(IaConfig.REINTENTOS_POR_DEFECTO).isEqualTo(1);
		assertThat(IaConfig.TOPE_DIARIO_TOTAL_POR_DEFECTO).isEqualTo(1000);
	}

	@Test
	void ollamaAceptaHttpsYHttpSoloLocalOPrivado() {
		for (String url : new String[] { "https://ollama.ejemplo.com", "https://ollama.ejemplo.com:443/", "http://localhost:11434",
				"http://LOCALHOST:11434", "http://127.0.0.1:11434", "http://[::1]:11434", "http://10.0.0.5:11434",
				"http://10.255.255.254", "http://172.16.0.1:11434", "http://172.31.255.255", "http://192.168.1.10:11434", "" }) {
			IaConfig.validarUrlDeOllama(url);
		}
		assertThat(armar("ollama", "", "", "http://192.168.1.10:11434", "")).isInstanceOf(AsistenteOllama.class);
	}

	@Test
	void ollamaRechazaHttpHaciaAfueraYDireccionesRaras() {
		for (String url : new String[] { "http://ollama.ejemplo.com:11434", "http://8.8.8.8:11434", "http://172.32.0.1", "http://172.15.0.1",
				"http://11.0.0.1", "http://192.169.1.1", "http://localhost.ejemplo.com", "http://192.168.1.10.ejemplo.com",
				"http://[2001:db8::1]:11434", "ftp://localhost", "localhost:11434", "no es una url", "http://" }) {
			assertThatThrownBy(() -> IaConfig.validarUrlDeOllama(url)).as(url).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("OLLAMA_URL");
		}
		assertThatThrownBy(() -> armar("ollama", "", "", "http://ollama.ejemplo.com:11434", ""))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("https");
	}

	@Test
	void laUrlAlternativaDeClaudeSoloSeAceptaConHttpsYSiNoElBackendNoArranca() {
		IaConfig.validarUrlDeClaude("");
		IaConfig.validarUrlDeClaude("   ");
		IaConfig.validarUrlDeClaude("https://proxy.de.pruebas");
		for (String url : new String[] { "http://127.0.0.1:9999", "http://localhost", "http://proxy.de.pruebas", "ftp://x", "basura" }) {
			assertThatThrownBy(() -> IaConfig.validarUrlDeClaude(url)).as(url).isInstanceOf(IllegalStateException.class)
					.hasMessageContaining("MICOMUNA_IA_CLAUDE_URL");
			assertThatThrownBy(() -> config.asistenteIa("claude", "20", "1", CLAVE, "", url, "", ""))
					.isInstanceOf(IllegalStateException.class);
		}
		assertThat(config.asistenteIa("claude", "20", "1", CLAVE, "", "https://proxy.de.pruebas", "", ""))
				.isInstanceOf(AsistenteClaude.class);
	}

	@Test
	void losNumerosDeLaConfiguracionVaciosOInvalidosUsanElValorPorDefecto() {
		assertThat(IaConfig.entero("30", 20, 1)).isEqualTo(30);
		assertThat(IaConfig.entero("", 20, 1)).isEqualTo(20);
		assertThat(IaConfig.entero("abc", 200, 0)).isEqualTo(200);
		assertThat(IaConfig.entero("0", 20, 1)).isEqualTo(1);
		assertThat(IaConfig.entero("0", 200, 0)).isZero();
		assertThat(IaConfig.entero("-5", 200, 0)).isZero();
	}
}
