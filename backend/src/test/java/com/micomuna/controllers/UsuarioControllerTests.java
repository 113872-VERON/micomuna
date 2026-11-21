package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.client.MailService;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// MC-14 (subtarea MC-54): el administrador de una comuna gestiona a los usuarios de su comuna.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsuarioControllerTests {

	private static final String RUTA = "/api/usuarios";
	private static final String SLUG_A = "t14-usuarios-a";
	private static final String CLAVE = "clave-segura-1";
	private static final Pattern CODIGO = Pattern.compile("codigo=([A-Za-z0-9_-]+)");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

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

	private Comuna comunaA;
	private Comuna comunaB;
	private Usuario adminA;
	private String tokenAdminA;

	@BeforeEach
	void preparar() {
		comunaA = comuna(SLUG_A);
		comunaB = comuna("t14-usuarios-b");
		adminA = usuario(comunaA, "admin-a@mail.com", "Admin A", Rol.ADMIN_COMUNA, CLAVE, true);
		tokenAdminA = bearer(adminA);
	}

	// ---- Ayudas

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, String nombre, Rol rol, String clave, boolean activo) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash(clave == null ? null : encoder.encode(clave));
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private Usuario pendiente(String correo) {
		return usuario(comunaA, correo, "Pendiente", Rol.AGENTE, null, true);
	}

	private String bearer(Usuario u) {
		return "Bearer " + jwt.generar(u);
	}

	private ResultActions listar(String token) throws Exception {
		return mockMvc.perform(get(RUTA).header("Authorization", token));
	}

	private ResultActions crear(String token, String cuerpo) throws Exception {
		return mockMvc.perform(post(RUTA).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions modificar(String token, long id, String cuerpo) throws Exception {
		return mockMvc.perform(patch(RUTA + "/" + id).header("Authorization", token)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions reenviar(String token, long id) throws Exception {
		return mockMvc.perform(post(RUTA + "/" + id + "/reenviar-invitacion").header("Authorization", token));
	}

	private ResultActions aceptarInvitacion(String codigo) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + SLUG_A + "/aceptar-invitacion")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"codigo\":\"" + codigo + "\",\"claveNueva\":\"mi-clave-elegida-1\"}"));
	}

	private ResultActions ingresar(String correo, String clave) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + SLUG_A + "/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}"));
	}

	private String codigoDelUltimoCorreo() {
		ArgumentCaptor<String> texto = ArgumentCaptor.forClass(String.class);
		verify(mail, atLeastOnce()).enviar(anyString(), anyString(), texto.capture());
		Matcher m = CODIGO.matcher(texto.getValue());
		assertThat(m.find()).as("el correo trae un enlace con código").isTrue();
		return m.group(1);
	}

	private long idCreado(String respuesta) throws Exception {
		return json.readTree(respuesta).get("usuario").get("id").asLong();
	}

	private Usuario recargar(Usuario u) {
		usuarios.flush();
		return usuarios.findById(u.getId()).orElseThrow();
	}

	private static String sinInstance(String cuerpo) {
		return cuerpo.replaceAll("\"instance\":\"[^\"]*\",?", "").replace(",}", "}");
	}

	// ---- Listado

	@Test
	void listaLosUsuariosDeMiComunaOrdenadosPorNombreYConSuEstado() throws Exception {
		usuario(comunaA, "zoe@mail.com", "Zoe", Rol.VECINO, CLAVE, true);
		usuario(comunaA, "ana@mail.com", "ana", Rol.AGENTE, null, true);
		// Pendiente y desactivado: se muestra INACTIVO (tiene prioridad).
		usuario(comunaA, "avila@mail.com", "Ávila", Rol.ADMINISTRATIVO, null, false);
		usuario(comunaA, "beto@mail.com", "Beto", Rol.VECINO, CLAVE, false);
		usuario(comunaB, "aaron@mail.com", "Aaron", Rol.VECINO, CLAVE, true);

		listar(tokenAdminA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre").value(contains("Admin A", "ana", "Ávila", "Beto", "Zoe")))
				.andExpect(jsonPath("$[*].estado").value(contains("ACTIVO", "PENDIENTE", "INACTIVO", "INACTIVO", "ACTIVO")))
				.andExpect(jsonPath("$[1].rol").value("AGENTE"))
				.andExpect(jsonPath("$[1].correo").value("ana@mail.com"))
				.andExpect(jsonPath("$[1].creadoEn").exists())
				.andExpect(jsonPath("$[0].claveHash").doesNotExist())
				.andExpect(jsonPath("$[0].clave").doesNotExist())
				.andExpect(content().string(not(containsString("aaron@mail.com"))))
				.andExpect(content().string(not(containsString("$2a$"))));
	}

	// ---- Alta

	@Test
	void altaCreaUnUsuarioPendienteYLeMandaLaInvitacion() throws Exception {
		String respuesta = crear(tokenAdminA, "{\"nombre\":\"  Carla Agente \",\"correo\":\" Carla@Mail.COM \",\"rol\":\"AGENTE\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invitacionEnviada").value(true))
				.andExpect(jsonPath("$.usuario.nombre").value("Carla Agente"))
				.andExpect(jsonPath("$.usuario.correo").value("carla@mail.com"))
				.andExpect(jsonPath("$.usuario.rol").value("AGENTE"))
				.andExpect(jsonPath("$.usuario.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.usuario.clave").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		Usuario creado = usuarios.findByIdAndComunaId(idCreado(respuesta), comunaA.getId()).orElseThrow();
		assertThat(creado.getClaveHash()).isNull();
		assertThat(creado.isActivo()).isTrue();
		verify(mail).enviar(eq("carla@mail.com"), eq("Te invitaron a MiComuna, Comuna de prueba"),
				org.mockito.ArgumentMatchers.contains("http://localhost:4200/" + SLUG_A + "/invitacion?codigo="));
		verify(mail).enviar(anyString(), anyString(), org.mockito.ArgumentMatchers.contains(", como Agente."));

		// La invitación sirve: elige su contraseña y entra con el login normal.
		aceptarInvitacion(codigoDelUltimoCorreo()).andExpect(status().isNoContent());
		ingresar("carla@mail.com", "mi-clave-elegida-1").andExpect(status().isOk());
	}

	@Test
	void siElCorreoNoSaleElUsuarioQuedaCreadoIgualYSeAvisa() throws Exception {
		doThrow(new MailSendException("SMTP caído")).when(mail).enviar(anyString(), anyString(), anyString());

		crear(tokenAdminA, "{\"nombre\":\"Sin correo\",\"correo\":\"sin-correo@mail.com\",\"rol\":\"ADMINISTRATIVO\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.invitacionEnviada").value(false))
				.andExpect(jsonPath("$.usuario.estado").value("PENDIENTE"));

		assertThat(usuarios.existsByComunaIdAndCorreo(comunaA.getId(), "sin-correo@mail.com")).isTrue();
	}

	@Test
	void sePuedeDarDeAltaAOtroAdministrador() throws Exception {
		crear(tokenAdminA, "{\"nombre\":\"Otra admin\",\"correo\":\"otra@mail.com\",\"rol\":\"ADMIN_COMUNA\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.rol").value("ADMIN_COMUNA"));
	}

	@Test
	void noSeDaDeAltaAVecinosNiAlSuperadmin() throws Exception {
		for (String rol : List.of("VECINO", "SUPERADMIN")) {
			crear(tokenAdminA, "{\"nombre\":\"X\",\"correo\":\"x-" + rol + "@mail.com\",\"rol\":\"" + rol + "\"}")
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("ROL_NO_PERMITIDO")).andExpect(jsonPath("$.detail").value(endsWith(".")));
			assertThat(usuarios.existsByComunaIdAndCorreo(comunaA.getId(), "x-" + rol.toLowerCase() + "@mail.com")).isFalse();
		}
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void altaConDatosInvalidosResponde400ConElErrorDeCadaCampo() throws Exception {
		crear(tokenAdminA, "{\"nombre\":\"\",\"correo\":\"no-es-correo\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.nombre").exists())
				.andExpect(jsonPath("$.errores.correo").exists())
				.andExpect(jsonPath("$.errores.rol").exists());
	}

	@Test
	void altaConUnCorreoQueYaEstaEnLaComunaResponde409() throws Exception {
		usuario(comunaA, "repetido@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		crear(tokenAdminA, "{\"nombre\":\"Otro\",\"correo\":\"REPETIDO@mail.com\",\"rol\":\"AGENTE\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void unCorreoDeOtraComunaNoCuentaComoRepetido() throws Exception {
		usuario(comunaB, "en-otra@mail.com", "Vecino B", Rol.VECINO, CLAVE, true);

		crear(tokenAdminA, "{\"nombre\":\"Nuevo\",\"correo\":\"en-otra@mail.com\",\"rol\":\"AGENTE\"}")
				.andExpect(status().isCreated());
	}

	@Test
	void elAltaSiempreVaALaComunaDelTokenAunqueElCuerpoTraigaOtra() throws Exception {
		String respuesta = crear(tokenAdminA, "{\"nombre\":\"Nuevo\",\"correo\":\"nuevo@mail.com\",\"rol\":\"AGENTE\","
				+ "\"idComuna\":" + comunaB.getId() + "}")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(usuarios.findByIdAndComunaId(idCreado(respuesta), comunaA.getId())).isPresent();
		assertThat(usuarios.existsByComunaIdAndCorreo(comunaB.getId(), "nuevo@mail.com")).isFalse();
	}

	// ---- Cambios: rol y activo

	@Test
	void cambiaElRolDeUnUsuario() throws Exception {
		Usuario vecino = usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		modificar(tokenAdminA, vecino.getId(), "{\"rol\":\"ADMINISTRATIVO\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("ADMINISTRATIVO"))
				.andExpect(jsonPath("$.estado").value("ACTIVO"));

		assertThat(recargar(vecino).getRol()).isEqualTo(Rol.ADMINISTRATIVO);
	}

	@Test
	void noSeAsignaElRolDeSuperadmin() throws Exception {
		Usuario vecino = usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		modificar(tokenAdminA, vecino.getId(), "{\"rol\":\"SUPERADMIN\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("ROL_NO_PERMITIDO")).andExpect(jsonPath("$.detail").value(endsWith(".")));
		assertThat(recargar(vecino).getRol()).isEqualTo(Rol.VECINO);
	}

	@Test
	void unPedidoSinCambiosResponde400() throws Exception {
		Usuario vecino = usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		modificar(tokenAdminA, vecino.getId(), "{}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PEDIDO_VACIO")).andExpect(jsonPath("$.detail").value(endsWith(".")));
	}

	@Test
	void desactivarNoBorraYReactivarDevuelveElAcceso() throws Exception {
		Usuario vecino = usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		modificar(tokenAdminA, vecino.getId(), "{\"activo\":false}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("INACTIVO"));
		assertThat(recargar(vecino).isActivo()).isFalse();
		ingresar("vecino@mail.com", CLAVE).andExpect(status().isUnauthorized());

		modificar(tokenAdminA, vecino.getId(), "{\"activo\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ACTIVO"));
		ingresar("vecino@mail.com", CLAVE).andExpect(status().isOk());
	}

	@Test
	void unUsuarioDesactivadoPierdeElAccesoAunqueSuTokenSigaVigente() throws Exception {
		Usuario otraAdmin = usuario(comunaA, "otra-admin@mail.com", "Otra admin", Rol.ADMIN_COMUNA, CLAVE, true);
		String token = bearer(otraAdmin);
		listar(token).andExpect(status().isOk());

		modificar(tokenAdminA, otraAdmin.getId(), "{\"activo\":false}").andExpect(status().isOk());

		listar(token).andExpect(status().isUnauthorized());
	}

	@Test
	void unCambioDeRolSeAplicaDeInmediato() throws Exception {
		Usuario administrativo = usuario(comunaA, "adm@mail.com", "Administrativo", Rol.ADMINISTRATIVO, CLAVE, true);
		String token = bearer(administrativo);
		listar(token).andExpect(status().isForbidden());

		modificar(tokenAdminA, administrativo.getId(), "{\"rol\":\"ADMIN_COMUNA\"}").andExpect(status().isOk());
		listar(token).andExpect(status().isOk());

		modificar(tokenAdminA, administrativo.getId(), "{\"rol\":\"AGENTE\"}").andExpect(status().isOk());
		listar(token).andExpect(status().isForbidden());
	}

	// ---- Reglas sobre uno mismo

	@Test
	void elAdministradorNoPuedeCambiarseElRolNiDesactivarse() throws Exception {
		modificar(tokenAdminA, adminA.getId(), "{\"rol\":\"AGENTE\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"))
				.andExpect(jsonPath("$.detail").value(containsString("rol")));
		modificar(tokenAdminA, adminA.getId(), "{\"activo\":false}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.detail").value(containsString("desactivar")));

		Usuario recargado = recargar(adminA);
		assertThat(recargado.getRol()).isEqualTo(Rol.ADMIN_COMUNA);
		assertThat(recargado.isActivo()).isTrue();
		listar(tokenAdminA).andExpect(status().isOk());
	}

	@Test
	void mandarseLosMismosValoresAUnoMismoNoEsUnCambio() throws Exception {
		modificar(tokenAdminA, adminA.getId(), "{\"rol\":\"ADMIN_COMUNA\",\"activo\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("ADMIN_COMUNA"))
				.andExpect(jsonPath("$.estado").value("ACTIVO"));
	}

	@Test
	void unAdministradorSiPuedeDesactivarAOtroAdministrador() throws Exception {
		Usuario otraAdmin = usuario(comunaA, "otra-admin@mail.com", "Otra admin", Rol.ADMIN_COMUNA, CLAVE, true);

		modificar(tokenAdminA, otraAdmin.getId(), "{\"activo\":false}").andExpect(status().isOk());
		modificar(tokenAdminA, otraAdmin.getId(), "{\"activo\":true,\"rol\":\"AGENTE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("AGENTE"))
				.andExpect(jsonPath("$.estado").value("ACTIVO"));
	}

	// ---- Corregir el correo

	@Test
	void corregirElCorreoDeUnPendienteDejaSinEfectoLaInvitacionAnterior() throws Exception {
		long id = idCreado(crear(tokenAdminA, "{\"nombre\":\"Mal escrito\",\"correo\":\"mal@mail.com\",\"rol\":\"AGENTE\"}")
				.andReturn().getResponse().getContentAsString());
		String viejo = codigoDelUltimoCorreo();

		modificar(tokenAdminA, id, "{\"correo\":\" Bien@Mail.com \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.correo").value("bien@mail.com"))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));

		// No se reenvía sola: solo salió el correo del alta.
		verify(mail, never()).enviar(eq("bien@mail.com"), anyString(), anyString());
		aceptarInvitacion(viejo).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));

		reenviar(tokenAdminA, id).andExpect(status().isNoContent());
		verify(mail).enviar(eq("bien@mail.com"), anyString(), anyString());
		aceptarInvitacion(codigoDelUltimoCorreo()).andExpect(status().isNoContent());
		ingresar("bien@mail.com", "mi-clave-elegida-1").andExpect(status().isOk());
	}

	@Test
	void noSeCorrigeElCorreoDeQuienYaEligioSuContrasena() throws Exception {
		Usuario vecino = usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		modificar(tokenAdminA, vecino.getId(), "{\"correo\":\"otro@mail.com\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
		assertThat(recargar(vecino).getCorreo()).isEqualTo("vecino@mail.com");
	}

	@Test
	void corregirElCorreoAUnoQueYaEstaEnLaComunaResponde409() throws Exception {
		usuario(comunaA, "ocupado@mail.com", "Vecino", Rol.VECINO, CLAVE, true);
		Usuario p = pendiente("pendiente@mail.com");

		modificar(tokenAdminA, p.getId(), "{\"correo\":\"ocupado@mail.com\"}")
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"));
		assertThat(recargar(p).getCorreo()).isEqualTo("pendiente@mail.com");
	}

	@Test
	void unCorreoMalFormadoResponde400() throws Exception {
		Usuario p = pendiente("pendiente@mail.com");

		modificar(tokenAdminA, p.getId(), "{\"correo\":\"no-es-correo\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.correo").exists());
	}

	// ---- Reenviar la invitación

	@Test
	void reenviarGeneraUnEnlaceNuevoEInvalidaElAnterior() throws Exception {
		long id = idCreado(crear(tokenAdminA, "{\"nombre\":\"Nuevo\",\"correo\":\"nuevo@mail.com\",\"rol\":\"AGENTE\"}")
				.andReturn().getResponse().getContentAsString());
		String viejo = codigoDelUltimoCorreo();

		reenviar(tokenAdminA, id).andExpect(status().isNoContent());
		String nuevo = codigoDelUltimoCorreo();

		assertThat(nuevo).isNotEqualTo(viejo);
		aceptarInvitacion(viejo).andExpect(status().isBadRequest());
		aceptarInvitacion(nuevo).andExpect(status().isNoContent());
	}

	@Test
	void noSeReenviaAQuienYaEligioSuContrasena() throws Exception {
		Usuario vecino = usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true);

		reenviar(tokenAdminA, vecino.getId())
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"))
				.andExpect(jsonPath("$.detail").value(containsString("ya eligió su contraseña")));
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void noSeReenviaAUnPendienteDesactivado() throws Exception {
		Usuario p = usuario(comunaA, "baja@mail.com", "Baja", Rol.AGENTE, null, false);

		reenviar(tokenAdminA, p.getId())
				.andExpect(status().isConflict()).andExpect(jsonPath("$.detail").value(endsWith(".")))
				.andExpect(jsonPath("$.detail").value(containsString("desactivado")));
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void reenviarSiElCorreoNoSaleResponde502() throws Exception {
		Usuario p = pendiente("pendiente@mail.com");
		doThrow(new MailSendException("SMTP caído")).when(mail).enviar(anyString(), anyString(), anyString());

		reenviar(tokenAdminA, p.getId())
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.codigo").value("CORREO_NO_ENVIADO"))
				.andExpect(jsonPath("$.detail").value("No pudimos enviar el correo. Probá de nuevo en unos minutos."));
	}

	@Test
	void unPendienteQuePasaAVecinoRecibeLaInvitacionComoVecino() throws Exception {
		Usuario p = pendiente("pendiente@mail.com");

		modificar(tokenAdminA, p.getId(), "{\"rol\":\"VECINO\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));
		reenviar(tokenAdminA, p.getId()).andExpect(status().isNoContent());

		verify(mail).enviar(eq("pendiente@mail.com"), eq("Te invitaron a MiComuna, Comuna de prueba"),
				org.mockito.ArgumentMatchers.contains(", como Vecino."));
	}

	// ---- Regla "al menos un administrador activo": solo cuentan los que ya eligieron su contraseña

	@Test
	void unAdministradorPendienteODesactivadoNoCuentaComoAdministradorConAcceso() {
		usuario(comunaA, "admin-pendiente@mail.com", "Pendiente", Rol.ADMIN_COMUNA, null, true);
		usuario(comunaA, "admin-baja@mail.com", "Baja", Rol.ADMIN_COMUNA, CLAVE, false);
		usuario(comunaA, "agente@mail.com", "Agente", Rol.AGENTE, CLAVE, true);
		usuario(comunaB, "admin-b@mail.com", "Admin B", Rol.ADMIN_COMUNA, CLAVE, true);

		// Solo adminA: activo, con contraseña y de esta comuna.
		assertThat(usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(comunaA.getId(), Rol.ADMIN_COMUNA))
				.isEqualTo(1);
	}

	@Test
	void conOtroAdministradorConAccesoSePuedeQuitarleElRolAUnoPendiente() throws Exception {
		Usuario adminPendiente = usuario(comunaA, "admin-pendiente@mail.com", "Pendiente", Rol.ADMIN_COMUNA, null, true);

		modificar(tokenAdminA, adminPendiente.getId(), "{\"rol\":\"AGENTE\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("AGENTE"));
	}

	// ---- Un pendiente desactivado no puede aceptar su invitación

	@Test
	void unPendienteDesactivadoNoPuedeAceptarSuInvitacion() throws Exception {
		long id = idCreado(crear(tokenAdminA, "{\"nombre\":\"Nuevo\",\"correo\":\"nuevo@mail.com\",\"rol\":\"AGENTE\"}")
				.andReturn().getResponse().getContentAsString());
		String codigo = codigoDelUltimoCorreo();

		modificar(tokenAdminA, id, "{\"activo\":false}").andExpect(status().isOk());
		aceptarInvitacion(codigo).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));

		// Al reactivarlo, el enlace viejo tampoco vuelve a servir: hay que reenviarle uno nuevo.
		modificar(tokenAdminA, id, "{\"activo\":true}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));
		aceptarInvitacion(codigo).andExpect(status().isBadRequest());
		clearInvocations(mail);
		reenviar(tokenAdminA, id).andExpect(status().isNoContent());
		aceptarInvitacion(codigoDelUltimoCorreo()).andExpect(status().isNoContent());
	}

	// ---- Aislamiento entre comunas

	@Test
	void unUsuarioDeOtraComunaRespondeIgualQueUnoInexistente() throws Exception {
		Usuario pendienteB = usuario(comunaB, "pendiente-b@mail.com", "Pendiente B", Rol.AGENTE, null, true);

		String patchOtra = modificar(tokenAdminA, pendienteB.getId(), "{\"activo\":false}")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andExpect(jsonPath("$.detail").value("No encontramos ese usuario."))
				.andReturn().getResponse().getContentAsString();
		String patchInexistente = modificar(tokenAdminA, 999999999L, "{\"activo\":false}")
				.andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		assertThat(sinInstance(patchOtra)).isEqualTo(sinInstance(patchInexistente));

		String reenviarOtra = reenviar(tokenAdminA, pendienteB.getId())
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andExpect(jsonPath("$.detail").value("No encontramos ese usuario."))
				.andReturn().getResponse().getContentAsString();
		String reenviarInexistente = reenviar(tokenAdminA, 999999999L)
				.andExpect(status().isNotFound())
				.andReturn().getResponse().getContentAsString();
		assertThat(sinInstance(reenviarOtra)).isEqualTo(sinInstance(reenviarInexistente));

		assertThat(recargar(pendienteB).isActivo()).isTrue();
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	// ---- Acceso por rol

	private MockHttpServletRequestBuilder[] losCuatroEndpoints(long id, String cuerpoCambio, String cuerpoAlta) {
		return new MockHttpServletRequestBuilder[] {
				get(RUTA),
				post(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpoAlta),
				patch(RUTA + "/" + id).contentType(MediaType.APPLICATION_JSON).content(cuerpoCambio),
				post(RUTA + "/" + id + "/reenviar-invitacion"),
		};
	}

	@Test
	void soloElAdministradorDeLaComunaUsaEstosEndpoints() throws Exception {
		Usuario p = pendiente("pendiente@mail.com");
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		List<Usuario> sinPermiso = List.of(
				usuario(comunaA, "vecino@mail.com", "Vecino", Rol.VECINO, CLAVE, true),
				usuario(comunaA, "agente@mail.com", "Agente", Rol.AGENTE, CLAVE, true),
				usuario(comunaA, "administrativo@mail.com", "Administrativo", Rol.ADMINISTRATIVO, CLAVE, true),
				usuario(null, "t14-root@mail.com", "Root", Rol.SUPERADMIN, CLAVE, true));

		for (Usuario u : sinPermiso) {
			String token = bearer(u);
			// Con cuerpos válidos y con cuerpos vacíos: el 403 llega antes que la validación.
			for (String[] cuerpos : List.of(
					new String[] { "{\"activo\":false}", "{\"nombre\":\"X\",\"correo\":\"x@mail.com\",\"rol\":\"AGENTE\"}" },
					new String[] { "{}", "{}" })) {
				for (MockHttpServletRequestBuilder endpoint : losCuatroEndpoints(p.getId(), cuerpos[0], cuerpos[1])) {
					mockMvc.perform(endpoint.header("Authorization", token))
							.andExpect(status().isForbidden())
							.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
				}
			}
		}

		assertThat(recargar(p).isActivo()).isTrue();
		assertThat(usuarios.existsByComunaIdAndCorreo(comunaA.getId(), "x@mail.com")).isFalse();
		verify(mail, never()).enviar(anyString(), anyString(), anyString());
	}

	@Test
	void sinTokenLosCuatroEndpointsResponden401() throws Exception {
		Usuario p = pendiente("pendiente@mail.com");
		for (MockHttpServletRequestBuilder endpoint : losCuatroEndpoints(p.getId(), "{\"activo\":false}",
				"{\"nombre\":\"X\",\"correo\":\"x@mail.com\",\"rol\":\"AGENTE\"}")) {
			mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
		}
		assertThat(recargar(p).isActivo()).isTrue();
	}
}
