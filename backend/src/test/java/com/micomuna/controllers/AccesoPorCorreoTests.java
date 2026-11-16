package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.client.MailService;
import com.micomuna.entities.CodigoRecuperacion;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.CodigoRecuperacionRepository;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccesoPorCorreoTests {

	private static final String SLUG = "t10-acceso";
	private static final Pattern CODIGO = Pattern.compile("codigo=([A-Za-z0-9_-]+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private CodigoRecuperacionRepository codigos;

	@Autowired
	private PasswordEncoder encoder;

	@MockitoBean
	private MailService mail;

	private Usuario usuarioDePrueba;

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, String clave, boolean activo) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash(clave == null ? null : encoder.encode(clave));
		u.setNombre("Vecino");
		u.setRol(Rol.VECINO);
		u.setActivo(activo);
		usuarioDePrueba = usuarios.saveAndFlush(u);
		return usuarioDePrueba;
	}

	// La base de desarrollo puede tener códigos de otras comunas: cada prueba mira solo los de su usuario.
	private List<CodigoRecuperacion> codigosDeLaPrueba() {
		return codigos.findPendientesDelUsuario(usuarioDePrueba.getId());
	}

	private ResultActions llamar(String slug, String accion, String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + slug + "/" + accion)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions recuperar(String slug, String correo) throws Exception {
		return llamar(slug, "recuperar-clave", "{\"correo\":\"" + correo + "\"}");
	}

	private ResultActions restablecer(String slug, String codigo, String clave) throws Exception {
		return llamar(slug, "restablecer-clave", "{\"codigo\":\"" + codigo + "\",\"claveNueva\":\"" + clave + "\"}");
	}

	private ResultActions aceptar(String slug, String codigo, String clave) throws Exception {
		return llamar(slug, "aceptar-invitacion", "{\"codigo\":\"" + codigo + "\",\"claveNueva\":\"" + clave + "\"}");
	}

	private ResultActions ingresar(String slug, String correo, String clave) throws Exception {
		return llamar(slug, "login", "{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}");
	}

	// El código viaja solo dentro del enlace del último correo enviado.
	private String ultimoCodigo() {
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(mail, atLeastOnce()).enviar(anyString(), anyString(), texto.capture());
		Matcher m = CODIGO.matcher(texto.getValue());
		assertThat(m.find()).as("el correo trae un enlace con código").isTrue();
		return m.group(1);
	}

	// ---- Pedir la recuperación

	@Test
	void recuperarEnviaUnCorreoConElEnlaceParaRestablecer() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);

		recuperar(SLUG, "vecino@mail.com").andExpect(status().isAccepted()).andExpect(content().string(""));

		verify(mail).enviar(eq("vecino@mail.com"), anyString(),
				contains("http://localhost:4200/" + SLUG + "/restablecer?codigo="));
	}

	@Test
	void recuperarNormalizaElCorreo() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);

		recuperar(SLUG, "  VECINO@Mail.com ").andExpect(status().isAccepted());

		verify(mail).enviar(eq("vecino@mail.com"), anyString(), anyString());
	}

	@Test
	void recuperarConCorreoDesconocidoResponde202SinEnviarNada() throws Exception {
		comuna(SLUG);

		recuperar(SLUG, "nadie@mail.com").andExpect(status().isAccepted());

		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void recuperarConUsuarioInactivoResponde202SinEnviarNada() throws Exception {
		usuario(comuna(SLUG), "baja@mail.com", "clave-vieja-1", false);

		recuperar(SLUG, "baja@mail.com").andExpect(status().isAccepted());

		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void recuperarNoEncuentraAlSuperadminNiAUsuariosDeOtraComuna() throws Exception {
		comuna(SLUG);
		Comuna otra = comuna("t10-otra");
		usuario(otra, "ajeno@mail.com", "clave-vieja-1", true);

		recuperar(SLUG, "ajeno@mail.com").andExpect(status().isAccepted());

		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void recuperarEnUnaComunaInexistenteDevuelve404() throws Exception {
		recuperar("no-existe", "vecino@mail.com").andExpect(status().isNotFound());
	}

	@Test
	void recuperarConCorreoInvalidoDevuelve400() throws Exception {
		comuna(SLUG);

		recuperar(SLUG, "no-es-correo").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.correo").exists());
	}

	@Test
	void siFallaElEnvioElPedidoIgualResponde202() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		doThrow(new MailSendException("SMTP caído")).when(mail).enviar(anyString(), anyString(), anyString());

		recuperar(SLUG, "vecino@mail.com").andExpect(status().isAccepted());
	}

	@Test
	void unUsuarioPendienteRecibeUnaInvitacionNuevaEnVezDeUnaRecuperacion() throws Exception {
		usuario(comuna(SLUG), "nuevo@mail.com", null, true);

		recuperar(SLUG, "nuevo@mail.com").andExpect(status().isAccepted());

		verify(mail).enviar(eq("nuevo@mail.com"), anyString(),
				contains("http://localhost:4200/" + SLUG + "/invitacion?codigo="));
	}

	// ---- Restablecer la clave

	@Test
	void restablecerCambiaLaClaveYPermiteIngresarConLaNueva() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");

		restablecer(SLUG, ultimoCodigo(), "clave-nueva-123").andExpect(status().isNoContent());

		ingresar(SLUG, "vecino@mail.com", "clave-nueva-123").andExpect(status().isOk());
		ingresar(SLUG, "vecino@mail.com", "clave-vieja-1").andExpect(status().isUnauthorized());
	}

	@Test
	void elCodigoSoloSirveUnaVez() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");
		String codigo = ultimoCodigo();

		restablecer(SLUG, codigo, "clave-nueva-123").andExpect(status().isNoContent());

		restablecer(SLUG, codigo, "otra-clave-456").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
		ingresar(SLUG, "vecino@mail.com", "clave-nueva-123").andExpect(status().isOk());
	}

	@Test
	void unCodigoVencidoNoSirve() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");
		String codigo = ultimoCodigo();
		for (CodigoRecuperacion c : codigosDeLaPrueba()) {
			c.setVenceEn(Instant.now().minus(1, ChronoUnit.MINUTES));
			codigos.saveAndFlush(c);
		}

		restablecer(SLUG, codigo, "clave-nueva-123").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
		ingresar(SLUG, "vecino@mail.com", "clave-vieja-1").andExpect(status().isOk());
	}

	@Test
	void unCodigoDeOtraComunaNoSirve() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		comuna("t10-otra");
		recuperar(SLUG, "vecino@mail.com");

		restablecer("t10-otra", ultimoCodigo(), "clave-nueva-123").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
	}

	@Test
	void unCodigoInventadoDaElMismoErrorQueUnoVencido() throws Exception {
		comuna(SLUG);

		restablecer(SLUG, "inventado", "clave-nueva-123").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"))
				.andExpect(jsonPath("$.detail").value("El enlace no es válido o ya venció. Pedí uno nuevo"));
	}

	@Test
	void unPedidoNuevoInvalidaElCodigoAnterior() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");
		String primero = ultimoCodigo();
		recuperar(SLUG, "vecino@mail.com");
		String segundo = ultimoCodigo();

		assertThat(segundo).isNotEqualTo(primero);
		restablecer(SLUG, primero, "clave-nueva-123").andExpect(status().isBadRequest());
		restablecer(SLUG, segundo, "clave-nueva-123").andExpect(status().isNoContent());
	}

	@Test
	void unaClaveInvalidaSeRechazaSinGastarElCodigo() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");
		String codigo = ultimoCodigo();

		restablecer(SLUG, codigo, "corta").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.claveNueva").exists());

		restablecer(SLUG, codigo, "clave-nueva-123").andExpect(status().isNoContent());
	}

	@Test
	void restablecerSinCodigoDevuelve400() throws Exception {
		comuna(SLUG);

		llamar(SLUG, "restablecer-clave", "{\"claveNueva\":\"clave-nueva-123\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.codigo").exists());
	}

	@Test
	void elCodigoNoSeGuardaEnClaro() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");
		String codigo = ultimoCodigo();

		assertThat(codigosDeLaPrueba()).hasSize(1).allSatisfy(c -> {
			assertThat(c.getCodigoHash()).isNotEqualTo(codigo).doesNotContain(codigo);
			assertThat(c.getVenceEn()).isBetween(Instant.now().plus(55, ChronoUnit.MINUTES),
					Instant.now().plus(61, ChronoUnit.MINUTES));
		});
	}

	// ---- Aceptar la invitación

	@Test
	void aceptarLaInvitacionDefineLaClaveYPermiteIngresar() throws Exception {
		usuario(comuna(SLUG), "nuevo@mail.com", null, true);
		recuperar(SLUG, "nuevo@mail.com");

		aceptar(SLUG, ultimoCodigo(), "mi-clave-elegida-1").andExpect(status().isNoContent());

		ingresar(SLUG, "nuevo@mail.com", "mi-clave-elegida-1").andExpect(status().isOk());
	}

	@Test
	void elCodigoDeInvitacionTambienSoloSirveUnaVez() throws Exception {
		usuario(comuna(SLUG), "nuevo@mail.com", null, true);
		recuperar(SLUG, "nuevo@mail.com");
		String codigo = ultimoCodigo();

		aceptar(SLUG, codigo, "mi-clave-elegida-1").andExpect(status().isNoContent());

		aceptar(SLUG, codigo, "otra-clave-456").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
	}

	@Test
	void unCodigoDeRecuperacionNoSirveComoInvitacion() throws Exception {
		usuario(comuna(SLUG), "vecino@mail.com", "clave-vieja-1", true);
		recuperar(SLUG, "vecino@mail.com");

		aceptar(SLUG, ultimoCodigo(), "clave-nueva-123").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
	}

	@Test
	void unCodigoDeInvitacionNoSirveParaRestablecer() throws Exception {
		usuario(comuna(SLUG), "nuevo@mail.com", null, true);
		recuperar(SLUG, "nuevo@mail.com");

		restablecer(SLUG, ultimoCodigo(), "clave-nueva-123").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
	}

	@Test
	void unaInvitacionVencidaNoSirve() throws Exception {
		usuario(comuna(SLUG), "nuevo@mail.com", null, true);
		recuperar(SLUG, "nuevo@mail.com");
		String codigo = ultimoCodigo();
		for (CodigoRecuperacion c : codigosDeLaPrueba()) {
			c.setVenceEn(Instant.now().minus(1, ChronoUnit.MINUTES));
			codigos.saveAndFlush(c);
		}

		aceptar(SLUG, codigo, "mi-clave-elegida-1").andExpect(status().isBadRequest());
	}
}
