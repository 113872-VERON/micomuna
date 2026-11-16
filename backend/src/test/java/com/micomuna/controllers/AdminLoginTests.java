package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.DatosToken;
import com.micomuna.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminLoginTests {

	private static final String CLAVE = "clave-del-superadmin-1";

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

	@BeforeEach
	void sinSuperadmin() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol, boolean activo) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash(encoder.encode(CLAVE));
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private ResultActions login(String correo, String clave) throws Exception {
		return mockMvc.perform(post("/api/admin/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}"));
	}

	@Test
	void elSuperadminIniciaSesionPorAdminLogin() throws Exception {
		Usuario u = usuario(null, "root@mail.com", Rol.SUPERADMIN, true);

		String respuesta = login("root@mail.com", CLAVE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.id").value(u.getId()))
				.andExpect(jsonPath("$.usuario.rol").value("SUPERADMIN"))
				.andExpect(jsonPath("$.usuario.comunaSlug").doesNotExist())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString(CLAVE))))
				.andReturn().getResponse().getContentAsString();

		DatosToken datos = jwt.leer(json.readTree(respuesta).get("token").asText());
		assertThat(datos.rol()).isEqualTo(Rol.SUPERADMIN);
		assertThat(datos.comunaId()).isNull();
	}

	@Test
	void elTokenDelSuperadminSirveParaEntrarARutasProtegidas() throws Exception {
		Usuario u = usuario(null, "root@mail.com", Rol.SUPERADMIN, true);

		String respuesta = login("root@mail.com", CLAVE).andReturn().getResponse().getContentAsString();
		String token = json.readTree(respuesta).get("token").asText();

		mockMvc.perform(get("/api/prueba/yo").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().string(u.getId() + ":SUPERADMIN:null"));
	}

	@Test
	void elCorreoNoDistingueMayusculasNiEspacios() throws Exception {
		usuario(null, "root@mail.com", Rol.SUPERADMIN, true);

		login(" ROOT@Mail.com ", CLAVE).andExpect(status().isOk());
	}

	@Test
	void unVecinoNoPuedeUsarAdminLogin() throws Exception {
		usuario(comuna("t7-vecino"), "ana@mail.com", Rol.VECINO, true);

		login("ana@mail.com", CLAVE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void elAdministradorDeUnaComunaTampocoPuedeUsarAdminLogin() throws Exception {
		usuario(comuna("t7-admin-comuna"), "jefe@mail.com", Rol.ADMIN_COMUNA, true);

		login("jefe@mail.com", CLAVE).andExpect(status().isUnauthorized());
	}

	@Test
	void unaClaveMalaYUnCorreoInexistenteDanExactamenteLaMismaRespuesta() throws Exception {
		usuario(null, "root@mail.com", Rol.SUPERADMIN, true);

		String claveMala = login("root@mail.com", "otra-clave-99")
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		String correoInexistente = login("nadie@mail.com", CLAVE)
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

		assertThat(correoInexistente).isEqualTo(claveMala);
		assertThat(claveMala).contains("Credenciales incorrectas");
	}

	@Test
	void unSuperadminDesactivadoNoPuedeEntrar() throws Exception {
		usuario(null, "root@mail.com", Rol.SUPERADMIN, false);

		login("root@mail.com", CLAVE).andExpect(status().isUnauthorized());
	}

	@Test
	void correoOClaveDemasiadoLargosDan400() throws Exception {
		login("a".repeat(250) + "@mail.com", "clave-segura-1").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.correo").exists());
		login("root@mail.com", "x".repeat(201)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.clave").exists());
	}

	@Test
	void unaClaveMuyLargaDa401YNoUnError500() throws Exception {
		usuario(null, "root@mail.com", Rol.SUPERADMIN, true);

		login("root@mail.com", "ñ".repeat(100)).andExpect(status().isUnauthorized());
	}

	@Test
	void rechazaDatosMalFormadosConErroresPorCampo() throws Exception {
		login("esto-no-es-un-correo", "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.correo").exists())
				.andExpect(jsonPath("$.errores.clave").exists());
	}

	@Test
	void soloElLoginEsPublicoYElRestoDeAdminRequiereToken() throws Exception {
		mockMvc.perform(get("/api/admin/comunas")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/admin/login")).andExpect(status().isUnauthorized());
	}
}
