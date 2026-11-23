package com.micomuna.services;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.micomuna.client.MailService;
import com.micomuna.configs.AsyncConfig;
import com.micomuna.constants.Fechas;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.models.CambioDeEstadoRegistrado;
import com.micomuna.models.CambioDeEstadoRegistrado.VecinoDeDuplicados;

// Los correos que reciben los vecinos cuando cambia el estado de un reclamo (MC-22 y MC-23: el de cierre).
//  - Se atienden DESPUÉS del commit (si la transacción se deshace no se manda nada) y en un hilo aparte (no hace esperar
//    a quien hizo el cambio).
//  - Son "al mejor esfuerzo": sin reintentos. Si el envío falla (o la cola de correos está llena) se registra el número de
//    reclamo y la comuna (nunca el correo ni datos personales) y el cambio queda como está.
//  - Los correos no llevan nombres del personal ni del agente.
//  - Los vecinos de reclamos descartados como duplicado reciben su propio aviso, sin el número del reclamo que sigue la
//    comuna ni datos de otro vecino.
@Service
public class AvisosDeReclamoService {

	private static final Logger log = LoggerFactory.getLogger(AvisosDeReclamoService.class);
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-AR"));

	static final String FRASE_DUPLICADO = "Otro vecino ya avisó de este mismo problema y la comuna lo está siguiendo. "
			+ "Te vamos a avisar de cada novedad.";

	private final MailService mail;
	private final Executor ejecutor;
	private final String urlFrontend;

	public AvisosDeReclamoService(MailService mail, @Qualifier(AsyncConfig.EJECUTOR_DE_CORREO) Executor ejecutor,
			@Value("${app.frontend-url}") String urlFrontend) {
		this.mail = mail;
		this.ejecutor = ejecutor;
		this.urlFrontend = urlFrontend.endsWith("/") ? urlFrontend.substring(0, urlFrontend.length() - 1) : urlFrontend;
	}

	// Corre en el hilo que hizo el commit, que no espera nada: solo entrega el trabajo al ejecutor acotado. Si la cola está
	// llena el aviso se pierde, pero con un registro que dice cuál fue (número y comuna).
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alCambiarElEstado(CambioDeEstadoRegistrado cambio) {
		try {
			ejecutor.execute(() -> avisar(cambio));
		} catch (RuntimeException e) {
			log.warn("Cola de correos llena: se perdió el aviso del reclamo n.º {} de la comuna {} ({})", cambio.numero(),
					cambio.comunaSlug(), e.getClass().getSimpleName());
		}
	}

	// Nunca lanza: cada correo se arma y se manda por separado, y un error en uno no corta los demás.
	void avisar(CambioDeEstadoRegistrado cambio) {
		enviar(cambio, cambio.vecinoCorreo(), () -> asunto(cambio), () -> texto(cambio));
		for (VecinoDeDuplicados vecino : cambio.duplicados()) {
			enviar(cambio, vecino.correo(), () -> asuntoDeDuplicados(cambio, vecino), () -> textoDeDuplicados(cambio, vecino));
		}
	}

	private void enviar(CambioDeEstadoRegistrado cambio, String destino, Supplier<String> asunto, Supplier<String> texto) {
		if (destino == null || destino.isBlank()) {
			return;
		}
		try {
			mail.enviar(destino, asunto.get(), texto.get());
		} catch (RuntimeException e) {
			log.warn("No se pudo enviar el aviso del reclamo n.º {} de la comuna {} ({})", cambio.numero(), cambio.comunaSlug(),
					e.getClass().getSimpleName());
		}
	}

	// ---- Al vecino del reclamo

	static String asunto(CambioDeEstadoRegistrado c) {
		return switch (c.estado()) {
			case RESUELTO -> "Tu reclamo n.º " + c.numero() + " fue resuelto";
			case DESCARTADO -> "Tu reclamo n.º " + c.numero() + " fue descartado";
			default -> "Tu reclamo n.º " + c.numero() + ": " + titulo(c.estado());
		};
	}

	String texto(CambioDeEstadoRegistrado c) {
		String comuna = CorreoAccesoService.conPalabraComuna(c.comunaNombre());
		StringBuilder t = new StringBuilder("Hola ").append(c.vecinoNombre()).append(",\n\n");
		if (c.estado() == EstadoReclamo.RESUELTO) {
			// MC-23: el correo de cierre.
			t.append("Buenas noticias: la ").append(comuna).append(" dio por resuelto tu reclamo n.º ").append(c.numero())
					.append(".\n");
		} else {
			t.append("Hay novedades de tu reclamo n.º ").append(c.numero()).append(" en ").append(comuna).append(".\n");
		}
		t.append("\nEstado: ").append(titulo(c.estado())).append("\n");
		t.append("Fecha: ").append(fecha(c)).append("\n");
		if (c.estado() == EstadoReclamo.ASIGNADO) {
			t.append("\nUna persona de la comuna se hizo cargo.\n");
		}
		if (c.estado() == EstadoReclamo.RESUELTO && hayTexto(c.trabajoRealizado())) {
			t.append("\nLo que se hizo: ").append(c.trabajoRealizado().strip()).append("\n");
		}
		if (c.estado() == EstadoReclamo.DESCARTADO) {
			t.append("\nLa comuna no pudo atender este reclamo.\n");
			if (c.motivoDescarte() == MotivoDescarte.DUPLICADO) {
				// Al vecino de un duplicado se le dice siempre lo mismo: ni motivo ni observación de la comuna.
				t.append(FRASE_DUPLICADO).append("\n");
			} else {
				t.append("Motivo: ").append(motivo(c.motivoDescarte())).append("\n");
				if (hayTexto(c.observacionDescarte())) {
					t.append("Lo que nos dijo la comuna: ").append(c.observacionDescarte().strip()).append("\n");
				} else if (c.motivoDescarte() == MotivoDescarte.OTRO || c.motivoDescarte() == null) {
					t.append("\n").append(comoContactar(c));
				}
			}
		}
		t.append("\nPodés seguir tu reclamo desde este enlace:\n").append(enlace(c, c.numero())).append("\n");
		return t.toString();
	}

	// ---- A los vecinos de reclamos duplicados del que cambió

	private static String asuntoDeDuplicados(CambioDeEstadoRegistrado c, VecinoDeDuplicados v) {
		boolean uno = v.numeros().size() == 1;
		return switch (c.estado()) {
			case RESUELTO -> uno ? "Se resolvió el problema que reportaste (reclamo n.º " + v.numeros().get(0) + ")"
					: "Se resolvió el problema que reportaste (reclamos n.º " + numeros(v.numeros()) + ")";
			case DESCARTADO -> uno ? "La comuna descartó el reclamo que seguía tu reclamo n.º " + v.numeros().get(0)
					: "La comuna descartó el reclamo que seguía tus reclamos n.º " + numeros(v.numeros());
			default -> uno ? "Novedades de tu reclamo n.º " + v.numeros().get(0)
					: "Novedades de tus reclamos n.º " + numeros(v.numeros());
		};
	}

	// Nunca menciona el número del reclamo que sigue la comuna ni nada de otro vecino.
	private String textoDeDuplicados(CambioDeEstadoRegistrado c, VecinoDeDuplicados v) {
		boolean uno = v.numeros().size() == 1;
		String propios = uno ? "el reclamo n.º " + v.numeros().get(0) : "los reclamos n.º " + numeros(v.numeros());
		StringBuilder t = new StringBuilder("Hola ").append(v.nombre()).append(",\n\n");
		switch (c.estado()) {
			case RESUELTO -> {
				t.append("La comuna resolvió ").append(uno ? "el problema que reportaste en " : "los problemas que reportaste en ")
						.append(propios).append(".\n");
				t.append("Fecha: ").append(fecha(c)).append("\n");
				if (hayTexto(c.trabajoRealizado())) {
					t.append("\nLo que se hizo: ").append(c.trabajoRealizado().strip()).append("\n");
				}
			}
			case DESCARTADO -> {
				t.append("La comuna descartó el reclamo que seguía ").append(uno ? "el problema que reportaste en "
						: "los problemas que reportaste en ").append(propios).append(". Si el problema sigue, comunicate con la comuna.\n");
				t.append("Fecha: ").append(fecha(c)).append("\n\n").append(comoContactar(c));
			}
			default -> {
				t.append("La comuna ya está atendiendo ").append(uno ? "el problema que reportaste en " : "los problemas que reportaste en ")
						.append(propios).append(". Hay novedades: ahora está «").append(enPalabras(c.estado())).append("».\n");
				t.append("Fecha: ").append(fecha(c)).append("\n");
			}
		}
		t.append(uno ? "\nPodés seguir tu reclamo desde este enlace:\n" : "\nPodés seguir tus reclamos desde estos enlaces:\n");
		for (Integer numero : v.numeros()) {
			t.append(enlace(c, numero)).append("\n");
		}
		return t.toString();
	}

	// ---- Textos

	// Teléfono y/o WhatsApp de la comuna si los cargó; si no, un aviso general.
	private static String comoContactar(CambioDeEstadoRegistrado c) {
		boolean telefono = hayTexto(c.comunaTelefono());
		boolean whatsapp = hayTexto(c.comunaWhatsapp());
		if (!telefono && !whatsapp) {
			return "Podés comunicarte con la comuna.\n";
		}
		StringBuilder t = new StringBuilder("Podés comunicarte con la comuna:\n");
		if (telefono) {
			t.append("Teléfono: ").append(c.comunaTelefono().strip()).append("\n");
		}
		if (whatsapp) {
			t.append("WhatsApp: ").append(c.comunaWhatsapp().strip()).append("\n");
		}
		return t.toString();
	}

	private String enlace(CambioDeEstadoRegistrado c, Integer numero) {
		return urlFrontend + "/" + c.comunaSlug() + "/reclamos/" + numero;
	}

	private static String fecha(CambioDeEstadoRegistrado c) {
		return FECHA.format(ZonedDateTime.ofInstant(c.fecha(), Fechas.ARGENTINA)) + " (hora de Argentina)";
	}

	private static boolean hayTexto(String texto) {
		return texto != null && !texto.isBlank();
	}

	// "5", "5 y 7", "5, 7 y 9".
	static String numeros(List<Integer> numeros) {
		if (numeros.size() == 1) {
			return String.valueOf(numeros.get(0));
		}
		String primeros = String.join(", ", numeros.subList(0, numeros.size() - 1).stream().map(String::valueOf).toList());
		return primeros + " y " + numeros.get(numeros.size() - 1);
	}

	static String enPalabras(EstadoReclamo estado) {
		return switch (estado) {
			case RECIBIDO -> "recibido";
			case ASIGNADO -> "asignado";
			case EN_REPARACION -> "en reparación";
			case RESUELTO -> "resuelto";
			case DESCARTADO -> "descartado";
		};
	}

	private static String titulo(EstadoReclamo estado) {
		String palabras = enPalabras(estado);
		return Character.toUpperCase(palabras.charAt(0)) + palabras.substring(1);
	}

	private static String motivo(MotivoDescarte motivo) {
		if (motivo == null) {
			return "Otro motivo";
		}
		return switch (motivo) {
			case NO_ES_DE_LA_COMUNA -> "No es un problema de esta comuna";
			case NO_SE_PUEDE_ATENDER -> "No se puede atender";
			case OTRO -> "Otro motivo";
			case DUPLICADO -> "Duplicado";
		};
	}
}
