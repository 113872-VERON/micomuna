package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.client.MailService;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaLogoRepository;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminComunaControllerTests {

	private static final String RUTA = "/api/admin/comunas";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private ComunaLogoRepository logos;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private JwtService jwt;

	@Autowired
	private ObjectMapper json;

	@MockitoBean
	private MailService mail;

	private String tokenSuperadmin;

	@BeforeEach
	void superadmin() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		tokenSuperadmin = jwt.generar(usuario(null, "root@mail.com", Rol.SUPERADMIN));
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash(encoder.encode("clave-segura-1"));
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private ResultActions crear(String token, String cuerpo) throws Exception {
		var pedido = post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		return mockMvc.perform(token == null ? pedido : pedido.header("Authorization", "Bearer " + token));
	}

	private String cuerpo(String nombre, String provincia, String extra) {
		return "{\"nombre\":\"" + nombre + "\",\"provincia\":\"" + provincia + "\","
				+ "\"adminCorreo\":\"jefe@mail.com\",\"adminNombre\":\"Jefe de la comuna\"" + extra + "}";
	}

	private ResultActions modificar(long id, String cuerpo) throws Exception {
		return mockMvc.perform(patch(RUTA + "/" + id).header("Authorization", "Bearer " + tokenSuperadmin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private long idDe(String respuesta) throws Exception {
		return json.readTree(respuesta).get("comuna").get("id").asLong();
	}

	// ---- Invitación por correo

	@Test
	void alCrearLaComunaSeLeEnviaLaInvitacionAlAdministrador() throws Exception {
		crear(tokenSuperadmin, cuerpo("Invitada", "CORDOBA", ",\"slug\":\"t10-invitada\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invitacionEnviada").value(true));

		verify(mail).enviar(eq("jefe@mail.com"), anyString(),
				contains("http://localhost:4200/t10-invitada/invitacion?codigo="));
	}

	// MC-14: el correo de invitación es el mismo para todos los roles; al administrador que crea el superadmin
	// le dice que entra como Administrador.
	@Test
	void laInvitacionDelAdministradorNombraLaComunaYElRol() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", ",\"slug\":\"t14-invitada\""))
				.andExpect(status().isCreated());

		verify(mail).enviar(eq("jefe@mail.com"), eq("Te invitaron a MiComuna, Comuna de Salsipuedes"),
				contains("Te invitaron a sumarte a MiComuna, Comuna de Salsipuedes, como Administrador."));
	}

	@Test
	void siFallaElCorreoLaComunaSeCreaIgualYSeAvisa() throws Exception {
		doThrow(new MailSendException("SMTP caído")).when(mail).enviar(anyString(), anyString(), anyString());

		crear(tokenSuperadmin, cuerpo("Sin correo", "CORDOBA", ",\"slug\":\"t10-sin-correo\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invitacionEnviada").value(false));

		assertThat(comunas.existsBySlug("t10-sin-correo")).isTrue();
	}

	@Test
	void reenviarGeneraUnaInvitacionNuevaEInvalidaLaAnterior() throws Exception {
		String respuesta = crear(tokenSuperadmin, cuerpo("Reenvio", "CORDOBA", ",\"slug\":\"t10-reenvio\""))
				.andReturn().getResponse().getContentAsString();
		long id = idDe(respuesta);
		String viejo = codigoDelUltimoCorreo();

		mockMvc.perform(post(RUTA + "/" + id + "/administrador/reenviar-invitacion").header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isNoContent());
		String nuevo = codigoDelUltimoCorreo();

		assertThat(nuevo).isNotEqualTo(viejo);
		aceptarInvitacion("t10-reenvio", viejo).andExpect(status().isBadRequest());
		aceptarInvitacion("t10-reenvio", nuevo).andExpect(status().isNoContent());
	}

	@Test
	void reenviarSiFallaElCorreoDevuelve502() throws Exception {
		long id = idDe(crear(tokenSuperadmin, cuerpo("Reenvio", "CORDOBA", ",\"slug\":\"t10-reenvio\""))
				.andReturn().getResponse().getContentAsString());
		doThrow(new MailSendException("SMTP caído")).when(mail).enviar(anyString(), anyString(), anyString());

		mockMvc.perform(post(RUTA + "/" + id + "/administrador/reenviar-invitacion").header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.codigo").value("CORREO_NO_ENVIADO"));
	}

	@Test
	void noSePuedeReenviarSiElAdministradorYaEligioSuClave() throws Exception {
		Comuna c = comuna("t10-activa");
		usuario(c, "jefe@mail.com", Rol.ADMIN_COMUNA);

		mockMvc.perform(post(RUTA + "/" + c.getId() + "/administrador/reenviar-invitacion").header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void reenviarEnUnaComunaInexistenteDevuelve404() throws Exception {
		mockMvc.perform(post(RUTA + "/999999/administrador/reenviar-invitacion").header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isNotFound());
	}

	@Test
	void reenviarExigeSerSuperadmin() throws Exception {
		Comuna c = comuna("t10-permisos");
		String tokenAdminComuna = jwt.generar(usuario(c, "otro@mail.com", Rol.ADMIN_COMUNA));

		mockMvc.perform(post(RUTA + "/" + c.getId() + "/administrador/reenviar-invitacion"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(RUTA + "/" + c.getId() + "/administrador/reenviar-invitacion").header("Authorization", "Bearer " + tokenAdminComuna))
				.andExpect(status().isForbidden());
	}

	private String codigoDelUltimoCorreo() {
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(mail, atLeastOnce()).enviar(anyString(), anyString(), texto.capture());
		Matcher m = Pattern.compile("codigo=([A-Za-z0-9_-]+)").matcher(texto.getValue());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	private ResultActions aceptarInvitacion(String slug, String codigo) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + slug + "/aceptar-invitacion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\",\"claveNueva\":\"mi-clave-elegida-1\"}"));
	}

	// ---- Alta

	@Test
	void creaUnaComunaConSuAdministradorPendiente() throws Exception {
		String respuesta = crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", ""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.nombre").value("Salsipuedes"))
				.andExpect(jsonPath("$.comuna.slug").value("salsipuedes-cordoba"))
				.andExpect(jsonPath("$.comuna.provincia").value("Córdoba"))
				.andExpect(jsonPath("$.comuna.activa").value(true))
				.andExpect(jsonPath("$.comuna.tieneLogo").value(false))
				.andExpect(jsonPath("$.adminCorreo").value("jefe@mail.com"))
				.andReturn().getResponse().getContentAsString();

		Comuna c = comunas.findById(idDe(respuesta)).orElseThrow();
		Usuario admin = usuarios.findByComunaIdAndCorreo(c.getId(), "jefe@mail.com").orElseThrow();
		assertThat(admin.getRol()).isEqualTo(Rol.ADMIN_COMUNA);
		assertThat(admin.getNombre()).isEqualTo("Jefe de la comuna");
		assertThat(admin.isActivo()).isTrue();
		// Sin contraseña: elige la suya con la invitación por correo. Nadie conoce una clave.
		assertThat(admin.getClaveHash()).isNull();
	}

	@Test
	void laRespuestaNoTraeNingunaClave() throws Exception {
		crear(tokenSuperadmin, cuerpo("Sin Clave", "CORDOBA", ""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.claveTemporal").doesNotExist())
				.andExpect(jsonPath("$.clave").doesNotExist())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("hash"))));
	}

	@Test
	void usaCelesteSiNoSeEligeColor() throws Exception {
		crear(tokenSuperadmin, cuerpo("Celeste", "CORDOBA", ""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.color").value("CELESTE"));
	}

	@Test
	void guardaColorDepartamentoYEnlacePersonalizado() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA",
				",\"color\":\"BOSQUE\",\"departamento\":\"Departamento Colón\",\"slug\":\"salsipuedes-colon-cordoba\""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.color").value("BOSQUE"))
				.andExpect(jsonPath("$.comuna.departamento").value("Departamento Colón"))
				.andExpect(jsonPath("$.comuna.slug").value("salsipuedes-colon-cordoba"));

		mockMvc.perform(get("/api/comunas/salsipuedes-colon-cordoba"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.color").value("BOSQUE"));
	}

	@Test
	void rechazaUnColorFueraDeLaPaleta() throws Exception {
		crear(tokenSuperadmin, cuerpo("Color Raro", "CORDOBA", ",\"color\":\"ROSA_FLUOR\""))
				.andExpect(status().isBadRequest());
		assertThat(comunas.existsBySlug("color-raro-cordoba")).isFalse();
	}

	@Test
	void rechazaUnSlugRepetidoConUn409() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", "")).andExpect(status().isCreated());

		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", ""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"))
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("departamento")));
	}

	@Test
	void unSlugRepetidoNoDejaUnAdministradorSuelto() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", "")).andExpect(status().isCreated());
		long antes = usuarios.count();

		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", "")).andExpect(status().isConflict());

		assertThat(usuarios.count()).isEqualTo(antes);
	}

	@Test
	void permiteElMismoNombreEnOtraProvincia() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", "")).andExpect(status().isCreated());

		crear(tokenSuperadmin, cuerpo("Salsipuedes", "NEUQUEN", ""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.slug").value("salsipuedes-neuquen"));
	}

	@Test
	void elMismoCorreoDeAdministradorPuedeUsarseEnOtraComuna() throws Exception {
		crear(tokenSuperadmin, cuerpo("Uno", "CORDOBA", "")).andExpect(status().isCreated());

		crear(tokenSuperadmin, cuerpo("Dos", "CORDOBA", "")).andExpect(status().isCreated());
	}

	@Test
	void normalizaElCorreoDelAdministradorYElEnlace() throws Exception {
		crear(tokenSuperadmin, "{\"nombre\":\" Salsipuedes \",\"provincia\":\"CORDOBA\",\"slug\":\"  Mi-Enlace \","
				+ "\"adminCorreo\":\"  JEFE@Mail.com \",\"adminNombre\":\" Jefe \"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.adminCorreo").value("jefe@mail.com"))
				.andExpect(jsonPath("$.comuna.slug").value("mi-enlace"))
				.andExpect(jsonPath("$.comuna.nombre").value("Salsipuedes"));
	}

	@Test
	void unEnlaceVacioSeTrataComoSiNoSeHubieraEnviado() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "CORDOBA", ",\"slug\":\"   \""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.slug").value("salsipuedes-cordoba"));
	}

	@Test
	void rechazaDatosInvalidosConErroresPorCampo() throws Exception {
		crear(tokenSuperadmin, "{\"nombre\":\"  \",\"adminCorreo\":\"no-es-correo\",\"adminNombre\":\"\","
				+ "\"slug\":\"Con Espacios\",\"departamento\":\"" + "x".repeat(81) + "\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.nombre").exists())
				.andExpect(jsonPath("$.errores.provincia").exists())
				.andExpect(jsonPath("$.errores.adminCorreo").exists())
				.andExpect(jsonPath("$.errores.adminNombre").exists())
				.andExpect(jsonPath("$.errores.slug").exists())
				.andExpect(jsonPath("$.errores.departamento").exists());
	}

	@Test
	void rechazaUnaProvinciaQueNoExiste() throws Exception {
		crear(tokenSuperadmin, cuerpo("Salsipuedes", "ATLANTIDA", "")).andExpect(status().isBadRequest());
	}

	@Test
	void rechazaUnNombreSinLetrasNiNumeros() throws Exception {
		crear(tokenSuperadmin, cuerpo("!!! ???", "CORDOBA", ""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("NOMBRE_INVALIDO"));
	}

	// ---- Permisos

	@Test
	void unVecinoNoPuedeCrearComunas() throws Exception {
		String token = jwt.generar(usuario(comuna("t9-vecino"), "ana@mail.com", Rol.VECINO));

		crear(token, cuerpo("Nueva", "CORDOBA", ""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		assertThat(comunas.existsBySlug("nueva-cordoba")).isFalse();
	}

	@Test
	void elAdministradorDeUnaComunaTampocoPuedeCrearComunas() throws Exception {
		String token = jwt.generar(usuario(comuna("t9-admin"), "jefe2@mail.com", Rol.ADMIN_COMUNA));

		crear(token, cuerpo("Nueva", "CORDOBA", "")).andExpect(status().isForbidden());
		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
	}

	@Test
	void sinTokenDevuelve401() throws Exception {
		crear(null, cuerpo("Nueva", "CORDOBA", "")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		mockMvc.perform(patch(RUTA + "/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isUnauthorized());
	}

	// ---- Lista y cambios

	@Test
	void laListaIncluyeLasInactivasYNoTraeElLogo() throws Exception {
		Comuna activa = comuna("t9-lista-activa");
		Comuna inactiva = comuna("t9-lista-inactiva");
		inactiva.setActiva(false);
		comunas.saveAndFlush(inactiva);
		logos.guardar(inactiva.getId(), new byte[] { 1, 2, 3 }, "image/png");

		String respuesta = mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"logo\""))))
				.andReturn().getResponse().getContentAsString();

		JsonNode lista = json.readTree(respuesta);
		JsonNode filaInactiva = null;
		boolean hayActiva = false;
		for (JsonNode fila : lista) {
			if (fila.get("id").asLong() == inactiva.getId()) {
				filaInactiva = fila;
			}
			hayActiva |= fila.get("id").asLong() == activa.getId();
		}
		assertThat(hayActiva).isTrue();
		assertThat(filaInactiva).isNotNull();
		assertThat(filaInactiva.get("activa").asBoolean()).isFalse();
		assertThat(filaInactiva.get("tieneLogo").asBoolean()).isTrue();
		assertThat(filaInactiva.get("slug").asText()).isEqualTo("t9-lista-inactiva");
	}

	@Test
	void cambiaNombreColorYDepartamento() throws Exception {
		Comuna c = comuna("t9-cambia");

		modificar(c.getId(), "{\"nombre\":\"Nombre nuevo\",\"color\":\"MARINO\",\"departamento\":\"Partido de Tigre\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Nombre nuevo"))
				.andExpect(jsonPath("$.color").value("MARINO"))
				.andExpect(jsonPath("$.departamento").value("Partido de Tigre"))
				.andExpect(jsonPath("$.slug").value("t9-cambia"));
	}

	@Test
	void unCambioParcialNoTocaLoQueNoSeEnvia() throws Exception {
		Comuna c = comuna("t9-parcial");
		c.setColor(ColorComuna.OCRE);
		c.setDepartamento("Departamento Colón");
		comunas.saveAndFlush(c);

		modificar(c.getId(), "{\"nombre\":\"Solo el nombre\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Solo el nombre"))
				.andExpect(jsonPath("$.color").value("OCRE"))
				.andExpect(jsonPath("$.departamento").value("Departamento Colón"))
				.andExpect(jsonPath("$.activa").value(true));
	}

	@Test
	void unDepartamentoVacioLoBorra() throws Exception {
		Comuna c = comuna("t9-borra-depto");
		c.setDepartamento("Departamento Colón");
		comunas.saveAndFlush(c);

		modificar(c.getId(), "{\"departamento\":\"\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.departamento").doesNotExist());
	}

	@Test
	void elEnlaceNoSePuedeCambiarDespuesDeCrearLaComuna() throws Exception {
		Comuna c = comuna("t9-enlace-fijo");

		modificar(c.getId(), "{\"slug\":\"otro-enlace\",\"nombre\":\"Nuevo\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("t9-enlace-fijo"));

		assertThat(comunas.findById(c.getId()).orElseThrow().getSlug()).isEqualTo("t9-enlace-fijo");
	}

	@Test
	void rechazaCambiosInvalidos() throws Exception {
		Comuna c = comuna("t9-invalido");

		modificar(c.getId(), "{\"nombre\":\"   \"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").exists());
		modificar(c.getId(), "{\"color\":\"ROSA_FLUOR\"}").andExpect(status().isBadRequest());
		modificar(c.getId(), "{\"departamento\":\"" + "x".repeat(81) + "\"}").andExpect(status().isBadRequest());
	}

	@Test
	void modificarUnaComunaQueNoExisteDa404() throws Exception {
		modificar(987654321L, "{\"nombre\":\"Nada\"}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void unIdQueNoEsNumeroDa400() throws Exception {
		mockMvc.perform(patch(RUTA + "/abc").header("Authorization", "Bearer " + tokenSuperadmin)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}

	// ---- Desactivar

	@Test
	void desactivarUnaComunaBloqueaElLoginLaConsultaPublicaYLosTokens() throws Exception {
		Comuna c = comuna("t9-desactivar");
		Usuario ana = usuario(c, "ana@mail.com", Rol.VECINO);
		String tokenAna = jwt.generar(ana);
		String credenciales = "{\"correo\":\"ana@mail.com\",\"clave\":\"clave-segura-1\"}";

		mockMvc.perform(post("/api/comunas/t9-desactivar/login").contentType(MediaType.APPLICATION_JSON)
				.content(credenciales)).andExpect(status().isOk());
		mockMvc.perform(get("/api/comunas/t9-desactivar")).andExpect(status().isOk());

		modificar(c.getId(), "{\"activa\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.activa").value(false));

		mockMvc.perform(post("/api/comunas/t9-desactivar/login").contentType(MediaType.APPLICATION_JSON)
				.content(credenciales)).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/comunas/t9-desactivar")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/prueba/yo").header("Authorization", "Bearer " + tokenAna))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void volverAActivarUnaComunaDevuelveElAcceso() throws Exception {
		Comuna c = comuna("t9-reactivar");
		usuario(c, "ana@mail.com", Rol.VECINO);
		modificar(c.getId(), "{\"activa\":false}").andExpect(status().isOk());

		modificar(c.getId(), "{\"activa\":true}").andExpect(status().isOk());

		mockMvc.perform(post("/api/comunas/t9-reactivar/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"ana@mail.com\",\"clave\":\"clave-segura-1\"}")).andExpect(status().isOk());
	}

	@Test
	void elAdministradorPendienteNoPuedeIniciarSesionTodavia() throws Exception {
		String respuesta = crear(tokenSuperadmin, cuerpo("Pendiente", "CORDOBA", ""))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String slug = json.readTree(respuesta).get("comuna").get("slug").asText();

		mockMvc.perform(post("/api/comunas/" + slug + "/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"jefe@mail.com\",\"clave\":\"cualquier-clave-1\"}"))
				.andExpect(status().isUnauthorized());
	}
}
