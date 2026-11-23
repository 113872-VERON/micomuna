package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.micomuna.client.MailService;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.models.CambioDeEstadoRegistrado;
import com.micomuna.models.CambioDeEstadoRegistrado.VecinoDeDuplicados;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// MC-22 y MC-23: el contenido de los correos de aviso, sin Spring ni base. Un MailService falso guarda lo que se manda.
class AvisosDeReclamoServiceTests {

	// 25/11/2026 13:15 UTC = 10:15 en Argentina.
	private static final Instant FECHA = Instant.parse("2026-11-25T13:15:00Z");

	private record Enviado(String destino, String asunto, String texto) {
	}

	private final List<Enviado> enviados = new ArrayList<>();
	private boolean falla;
	private String fallaSoloPara;
	private AvisosDeReclamoService avisos;
	private Logger logger;
	private ListAppender<ILoggingEvent> registro;

	@BeforeEach
	void preparar() {
		MailService mail = (destino, asunto, texto) -> {
			if (falla || destino.equals(fallaSoloPara)) {
				throw new IllegalStateException("SMTP caído para " + destino);
			}
			enviados.add(new Enviado(destino, asunto, texto));
		};
		avisos = new AvisosDeReclamoService(mail, Runnable::run, "http://localhost:4200/");
		logger = (Logger) LoggerFactory.getLogger(AvisosDeReclamoService.class);
		registro = new ListAppender<>();
		registro.start();
		logger.addAppender(registro);
	}

	@AfterEach
	void soltar() {
		logger.detachAppender(registro);
	}

	private static CambioDeEstadoRegistrado cambio(EstadoReclamo estado, MotivoDescarte motivo, String observacion,
			String trabajo, List<VecinoDeDuplicados> duplicados, String telefono, String whatsapp) {
		return new CambioDeEstadoRegistrado("Salsipuedes", "salsipuedes", telefono, whatsapp, 12, "ana@mail.com", "Ana",
				estado, FECHA, motivo, observacion, trabajo, duplicados);
	}

	private static CambioDeEstadoRegistrado cambio(EstadoReclamo estado, MotivoDescarte motivo, String observacion,
			String trabajo, List<VecinoDeDuplicados> duplicados) {
		return cambio(estado, motivo, observacion, trabajo, duplicados, null, null);
	}

	private static CambioDeEstadoRegistrado cambio(EstadoReclamo estado) {
		return cambio(estado, null, null, null, List.of());
	}

	@Test
	void cadaCambioDeEstadoAvisaConComunaNumeroEstadoFechaYEnlace() {
		for (EstadoReclamo estado : List.of(EstadoReclamo.RECIBIDO, EstadoReclamo.ASIGNADO, EstadoReclamo.EN_REPARACION)) {
			enviados.clear();
			avisos.avisar(cambio(estado));

			assertThat(enviados).hasSize(1);
			Enviado e = enviados.get(0);
			assertThat(e.destino()).isEqualTo("ana@mail.com");
			assertThat(e.asunto()).startsWith("Tu reclamo n.º 12: ");
			assertThat(e.texto()).contains("Hola Ana").contains("reclamo n.º 12").contains("Comuna de Salsipuedes")
					.contains("Fecha: 25/11/2026 10:15 (hora de Argentina)")
					.contains("http://localhost:4200/salsipuedes/reclamos/12");
		}
		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.EN_REPARACION));
		assertThat(enviados.get(0).asunto()).isEqualTo("Tu reclamo n.º 12: En reparación");
		assertThat(enviados.get(0).texto()).contains("Estado: En reparación").doesNotContain("se hizo cargo");
	}

	@Test
	void asignadoAgregaQueUnaPersonaDeLaComunaSeHizoCargo() {
		avisos.avisar(cambio(EstadoReclamo.ASIGNADO));

		assertThat(enviados.get(0).texto()).contains("Una persona de la comuna se hizo cargo.");
	}

	@Test
	void unNombreDeComunaQueYaEmpiezaConComunaNoSeRepite() {
		avisos.avisar(new CambioDeEstadoRegistrado("Comuna de Salsipuedes", "salsipuedes", null, null, 1, "ana@mail.com", "Ana",
				EstadoReclamo.ASIGNADO, FECHA, null, null, null, List.of()));

		assertThat(enviados.get(0).texto()).contains("en Comuna de Salsipuedes.").doesNotContain("Comuna de Comuna");
	}

	@Test
	void elDescarteDiceQueNoSePudoAtender_elMotivoEnPalabrasYLaObservacionDeLaComuna() {
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.NO_SE_PUEDE_ATENDER, " Requiere una obra mayor ", null,
				List.of()));

		Enviado e = enviados.get(0);
		assertThat(e.asunto()).isEqualTo("Tu reclamo n.º 12 fue descartado");
		assertThat(e.texto()).contains("Estado: Descartado").contains("La comuna no pudo atender este reclamo.")
				.contains("Motivo: No se puede atender").contains("Lo que nos dijo la comuna: Requiere una obra mayor")
				.doesNotContain("Podés comunicarte");

		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.NO_ES_DE_LA_COMUNA, "Es de la ruta", null, List.of()));
		assertThat(enviados.get(0).texto()).contains("Motivo: No es un problema de esta comuna");
	}

	@Test
	void conMotivoOtroSinObservacionExplicaComoContactarALaComuna() {
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.OTRO, null, null, List.of(), "3511234567", "5493511234567"));
		assertThat(enviados.get(0).texto()).contains("Motivo: Otro motivo").contains("Podés comunicarte con la comuna:")
				.contains("Teléfono: 3511234567").contains("WhatsApp: 5493511234567");

		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.OTRO, null, null, List.of(), null, "5493511234567"));
		assertThat(enviados.get(0).texto()).contains("WhatsApp: 5493511234567").doesNotContain("Teléfono:");

		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.OTRO, null, null, List.of()));
		assertThat(enviados.get(0).texto()).contains("Podés comunicarte con la comuna.").doesNotContain("Teléfono")
				.doesNotContain("WhatsApp");

		// Con una observación escrita no hace falta.
		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.OTRO, "Era una broma", null, List.of(), "3511234567", null));
		assertThat(enviados.get(0).texto()).contains("Lo que nos dijo la comuna: Era una broma").doesNotContain("Teléfono");
	}

	@Test
	void unDuplicadoUsaLaFraseFijaYNuncaLlevaLaObservacionNiNombraAlOriginal() {
		// Aunque hubiera una observación guardada de antes, no viaja al vecino del duplicado.
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.DUPLICADO, "Mismo pozo de la esquina", null, List.of()));

		String texto = enviados.get(0).texto();
		assertThat(texto).contains("La comuna no pudo atender este reclamo.")
				.contains("Otro vecino ya avisó de este mismo problema y la comuna lo está siguiendo. "
						+ "Te vamos a avisar de cada novedad.")
				.doesNotContain("Mismo pozo").doesNotContain("Lo que nos dijo la comuna").doesNotContain("Motivo:")
				.doesNotContain("Duplicado");
	}

	@Test
	void elCierreTieneAsuntoPropioYLoQueSeHizo() {
		avisos.avisar(cambio(EstadoReclamo.RESUELTO, null, null, "  Se tapó el pozo con asfalto ", List.of()));

		Enviado e = enviados.get(0);
		assertThat(e.asunto()).isEqualTo("Tu reclamo n.º 12 fue resuelto");
		assertThat(e.texto()).contains("Buenas noticias").contains("dio por resuelto tu reclamo n.º 12")
				.contains("Estado: Resuelto").contains("Lo que se hizo: Se tapó el pozo con asfalto")
				.contains("http://localhost:4200/salsipuedes/reclamos/12");

		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.RESUELTO, null, null, "   ", List.of()));
		assertThat(enviados.get(0).texto()).doesNotContain("Lo que se hizo");
		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.RESUELTO, null, null, null, List.of()));
		assertThat(enviados.get(0).texto()).doesNotContain("Lo que se hizo");
	}

	@Test
	void losVecinosDeDuplicadosRecibenSuPropioAvisoSinElNumeroDelOriginal() {
		List<VecinoDeDuplicados> duplicados = List.of(new VecinoDeDuplicados("bea@mail.com", "Bea", List.of(30)),
				new VecinoDeDuplicados("caro@mail.com", "Caro", List.of(31, 35, 40)));

		avisos.avisar(cambio(EstadoReclamo.EN_REPARACION, null, null, null, duplicados));

		assertThat(enviados).hasSize(3);
		assertThat(enviados.get(0).destino()).isEqualTo("ana@mail.com");
		Enviado bea = enviados.get(1);
		assertThat(bea.destino()).isEqualTo("bea@mail.com");
		assertThat(bea.asunto()).isEqualTo("Novedades de tu reclamo n.º 30");
		assertThat(bea.texto()).contains("La comuna ya está atendiendo el problema que reportaste en el reclamo n.º 30. "
				+ "Hay novedades: ahora está «en reparación».").contains("http://localhost:4200/salsipuedes/reclamos/30")
				// Ni el número del original (12) ni su vecino.
				.doesNotContain("n.º 12").doesNotContain("/reclamos/12").doesNotContain("Ana").doesNotContain("ana@mail.com");
		Enviado caro = enviados.get(2);
		assertThat(caro.asunto()).isEqualTo("Novedades de tus reclamos n.º 31, 35 y 40");
		assertThat(caro.texto()).contains("La comuna ya está atendiendo los problemas que reportaste en los reclamos n.º 31, 35 y 40. "
				+ "Hay novedades: ahora está «en reparación».")
				.contains("/reclamos/31").contains("/reclamos/35").contains("/reclamos/40").doesNotContain("/reclamos/12");
	}

	@Test
	void siElOriginalSeDescartaSusDuplicadosRecibenUnAvisoPropioConComoContactar() {
		List<VecinoDeDuplicados> duplicados = List.of(new VecinoDeDuplicados("bea@mail.com", "Bea", List.of(30)),
				new VecinoDeDuplicados("caro@mail.com", "Caro", List.of(31, 35)));

		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.NO_SE_PUEDE_ATENDER, "Obra mayor", null, duplicados,
				"3511234567", "5493511234567"));

		assertThat(enviados).hasSize(3);
		Enviado bea = enviados.get(1);
		assertThat(bea.asunto()).isEqualTo("La comuna descartó el reclamo que seguía tu reclamo n.º 30");
		assertThat(bea.texto()).contains("La comuna descartó el reclamo que seguía el problema que reportaste en el reclamo n.º 30. "
				+ "Si el problema sigue, comunicate con la comuna.").contains("Teléfono: 3511234567")
				.contains("WhatsApp: 5493511234567").contains("/reclamos/30")
				// Nada del descarte del original: ni su motivo ni su observación, ni su número.
				.doesNotContain("Obra mayor").doesNotContain("No se puede atender").doesNotContain("n.º 12")
				.doesNotContain("/reclamos/12").doesNotContain("Ana");
		Enviado caro = enviados.get(2);
		assertThat(caro.asunto()).isEqualTo("La comuna descartó el reclamo que seguía tus reclamos n.º 31 y 35");
		assertThat(caro.texto()).contains("los problemas que reportaste en los reclamos n.º 31 y 35");

		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.DESCARTADO, MotivoDescarte.OTRO, "x", null, duplicados));
		assertThat(enviados.get(1).texto()).contains("Podés comunicarte con la comuna.");
	}

	@Test
	void siElOriginalSeResuelveSusDuplicadosRecibenElCierreSinDatosDelOriginal() {
		List<VecinoDeDuplicados> duplicados = List.of(new VecinoDeDuplicados("bea@mail.com", "Bea", List.of(30)),
				new VecinoDeDuplicados("caro@mail.com", "Caro", List.of(31, 35)));

		avisos.avisar(cambio(EstadoReclamo.RESUELTO, null, null, "Se tapó el pozo", duplicados));

		assertThat(enviados).hasSize(3);
		Enviado bea = enviados.get(1);
		assertThat(bea.asunto()).isEqualTo("Se resolvió el problema que reportaste (reclamo n.º 30)");
		assertThat(bea.texto()).contains("La comuna resolvió el problema que reportaste en el reclamo n.º 30.")
				.contains("Lo que se hizo: Se tapó el pozo").contains("/reclamos/30").doesNotContain("n.º 12")
				.doesNotContain("/reclamos/12").doesNotContain("Ana");
		assertThat(enviados.get(2).asunto()).isEqualTo("Se resolvió el problema que reportaste (reclamos n.º 31 y 35)");

		enviados.clear();
		avisos.avisar(cambio(EstadoReclamo.RESUELTO, null, null, null, duplicados));
		assertThat(enviados.get(1).texto()).doesNotContain("Lo que se hizo");
	}

	@Test
	void unCorreoQueFallaSeRegistraConNumeroYComunaSinElCorreo_yNoCortaLosDemas() {
		falla = true;
		List<VecinoDeDuplicados> duplicados = List.of(new VecinoDeDuplicados("bea@mail.com", "Bea", List.of(30)));

		avisos.avisar(cambio(EstadoReclamo.ASIGNADO, null, null, null, duplicados));

		// Se intentaron los dos y los dos fallaron: dos avisos en el log, sin lanzar nada.
		assertThat(registro.list).hasSize(2);
		for (ILoggingEvent evento : registro.list) {
			assertThat(evento.getLevel()).isEqualTo(Level.WARN);
			String mensaje = evento.getFormattedMessage();
			assertThat(mensaje).contains("n.º 12").contains("salsipuedes").contains("IllegalStateException")
					.doesNotContain("@").doesNotContain("Ana").doesNotContain("SMTP");
		}
	}

	@Test
	void siFallaElEnvioDeUnoElSiguienteIgualSale() {
		fallaSoloPara = "ana@mail.com";
		List<VecinoDeDuplicados> duplicados = List.of(new VecinoDeDuplicados("bea@mail.com", "Bea", List.of(30)));

		avisos.avisar(cambio(EstadoReclamo.ASIGNADO, null, null, null, duplicados));

		assertThat(enviados).extracting(Enviado::destino).containsExactly("bea@mail.com");
		assertThat(registro.list).hasSize(1);
	}

	@Test
	void unErrorAlArmarElTextoDeUnVecinoNoCortaLosDemasYQuedaEnElLog() {
		// Un vecino de duplicados sin números hace fallar al armar su asunto; los otros y el del reclamo siguen.
		List<VecinoDeDuplicados> duplicados = List.of(new VecinoDeDuplicados("roto@mail.com", "Roto", List.of()),
				new VecinoDeDuplicados("bea@mail.com", "Bea", List.of(30)));

		avisos.avisar(cambio(EstadoReclamo.ASIGNADO, null, null, null, duplicados));

		assertThat(enviados).extracting(Enviado::destino).containsExactly("ana@mail.com", "bea@mail.com");
		assertThat(registro.list).hasSize(1);
		assertThat(registro.list.get(0).getFormattedMessage()).contains("n.º 12").contains("salsipuedes")
				.doesNotContain("roto@mail.com");
	}

	@Test
	void conLaColaLlenaElAvisoPerdidoSeRegistraConNumeroYComuna() {
		AvisosDeReclamoService conColaLlena = new AvisosDeReclamoService((d, a, t) -> enviados.add(new Enviado(d, a, t)),
				tarea -> {
					throw new RejectedExecutionException("cola llena");
				}, "http://localhost:4200");

		conColaLlena.alCambiarElEstado(cambio(EstadoReclamo.ASIGNADO));

		assertThat(enviados).isEmpty();
		assertThat(registro.list).hasSize(1);
		assertThat(registro.list.get(0).getLevel()).isEqualTo(Level.WARN);
		assertThat(registro.list.get(0).getFormattedMessage()).contains("n.º 12").contains("salsipuedes").doesNotContain("@");
	}

	@Test
	void unDestinoVacioNoManda() {
		avisos.avisar(new CambioDeEstadoRegistrado("Salsipuedes", "salsipuedes", null, null, 1, " ", "Ana", EstadoReclamo.ASIGNADO,
				FECHA, null, null, null, List.of()));

		assertThat(enviados).isEmpty();
	}
}
