package com.micomuna.services;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.micomuna.client.MailService;
import com.micomuna.enums.Rol;
import com.micomuna.enums.TipoCodigo;

// Arma y manda los correos con enlace. Nunca lanza: si el envío falla devuelve false y quien llama decide.
@Service
public class CorreoAccesoService {

	private static final Logger log = LoggerFactory.getLogger(CorreoAccesoService.class);

	private final MailService mail;
	private final String urlFrontend;

	public CorreoAccesoService(MailService mail, @Value("${app.frontend-url}") String urlFrontend) {
		this.mail = mail;
		this.urlFrontend = urlFrontend.endsWith("/") ? urlFrontend.substring(0, urlFrontend.length() - 1) : urlFrontend;
	}

	// Datos sueltos (no entidades) para poder usarlos cuando la transacción ya terminó.
	// El rol se nombra en la invitación ("como Agente"); en la recuperación de la clave no se usa.
	public record Destinatario(String correo, String nombre, String comunaNombre, String slug, Rol rol) {
	}

	public boolean enviar(Destinatario para, TipoCodigo tipo, String codigo) {
		boolean invitacion = tipo == TipoCodigo.INVITACION;
		String enlace = urlFrontend + "/" + para.slug() + (invitacion ? "/invitacion" : "/restablecer") + "?codigo=" + codigo;
		String asunto = invitacion ? "Te invitaron a MiComuna, " + conPalabraComuna(para.comunaNombre())
				: "Recuperá tu contraseña de MiComuna";
		String texto = invitacion
				? "Hola " + para.nombre() + ",\n\nTe invitaron a sumarte a MiComuna, " + conPalabraComuna(para.comunaNombre())
						+ ", como " + para.rol().getNombre()
						+ ". Elegí tu contraseña desde este enlace (sirve una sola vez y vence en 72 horas):\n\n"
						+ enlace + "\n"
				: "Hola " + para.nombre() + ",\n\nPediste recuperar tu contraseña de " + para.comunaNombre()
						+ " en MiComuna. Elegí una nueva desde este enlace (sirve una sola vez y vence en 60 minutos):\n\n"
						+ enlace + "\n\nSi no lo pediste vos, ignorá este correo: tu contraseña sigue igual.\n";
		try {
			mail.enviar(para.correo(), asunto, texto);
			return true;
		} catch (RuntimeException e) {
			// Sin el enlace ni el correo en el registro: el código es un secreto.
			log.warn("No se pudo enviar el correo de {} ({})", tipo, e.getClass().getSimpleName());
			return false;
		}
	}

	// "Salsipuedes" -> "Comuna de Salsipuedes". Si el nombre ya empieza con "Comuna", no se repite.
	static String conPalabraComuna(String comunaNombre) {
		return comunaNombre.toLowerCase(Locale.ROOT).startsWith("comuna") ? comunaNombre : "Comuna de " + comunaNombre;
	}
}
