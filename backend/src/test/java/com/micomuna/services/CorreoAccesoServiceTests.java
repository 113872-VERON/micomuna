package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.micomuna.client.MailService;
import com.micomuna.enums.Rol;
import com.micomuna.enums.TipoCodigo;
import com.micomuna.services.CorreoAccesoService.Destinatario;

// Textos de los correos con enlace: la invitación sirve para cualquier rol y lo nombra; la recuperación no cambia.
class CorreoAccesoServiceTests {

	private final MailService mail = mock(MailService.class);
	private final CorreoAccesoService correo = new CorreoAccesoService(mail, "http://localhost:4200/");

	private String[] enviado(Destinatario para, TipoCodigo tipo) {
		ArgumentCaptor<String> asunto = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		assertThat(correo.enviar(para, tipo, "abc123")).isTrue();
		verify(mail).enviar(eq(para.correo()), asunto.capture(), texto.capture());
		return new String[] { asunto.getValue(), texto.getValue() };
	}

	@Test
	void laInvitacionNombraLaComunaYElRolEnTextoClaro() {
		Map<Rol, String> esperado = Map.of(
				Rol.AGENTE, "como Agente",
				Rol.ADMINISTRATIVO, "como Personal administrativo",
				Rol.ADMIN_COMUNA, "como Administrador",
				Rol.VECINO, "como Vecino");
		esperado.forEach((rol, comoRol) -> {
			String destino = rol.name().toLowerCase() + "@mail.com";
			String[] correoEnviado = enviado(new Destinatario(destino, "Ana", "Salsipuedes", "salsipuedes-cordoba", rol),
					TipoCodigo.INVITACION);

			assertThat(correoEnviado[0]).isEqualTo("Te invitaron a MiComuna, Comuna de Salsipuedes");
			assertThat(correoEnviado[1])
					.startsWith("Hola Ana,")
					.contains("Comuna de Salsipuedes, " + comoRol + ".")
					.contains("http://localhost:4200/salsipuedes-cordoba/invitacion?codigo=abc123")
					.doesNotContain("administrar");
		});
	}

	@Test
	void siElNombreYaDiceComunaNoSeRepite() {
		String[] correoEnviado = enviado(new Destinatario("a@mail.com", "Ana", "Comuna de Prueba", "prueba", Rol.AGENTE),
				TipoCodigo.INVITACION);

		assertThat(correoEnviado[0]).isEqualTo("Te invitaron a MiComuna, Comuna de Prueba");
	}

	@Test
	void laRecuperacionDeClaveSigueIgual() {
		String[] correoEnviado = enviado(new Destinatario("a@mail.com", "Ana", "Salsipuedes", "salsipuedes-cordoba",
				Rol.AGENTE), TipoCodigo.RECUPERACION);

		assertThat(correoEnviado[0]).isEqualTo("Recuperá tu contraseña de MiComuna");
		assertThat(correoEnviado[1]).isEqualTo("Hola Ana,\n\nPediste recuperar tu contraseña de Salsipuedes"
				+ " en MiComuna. Elegí una nueva desde este enlace (sirve una sola vez y vence en 60 minutos):\n\n"
				+ "http://localhost:4200/salsipuedes-cordoba/restablecer?codigo=abc123"
				+ "\n\nSi no lo pediste vos, ignorá este correo: tu contraseña sigue igual.\n");
	}
}
