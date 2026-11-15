package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AuthControllerLoginTests {

	private static final String CLAVE = "clave-segura-1";

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

	private Comuna comuna(String slug, boolean activa) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		c.setActiva(activa);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol, boolean activo) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash(encoder.encode(CLAVE));
		u.setNombre("Ana");
		u.setRol(rol);
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private ResultActions login(String slug, String correo, String clave) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + slug + "/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}"));
	}

	@Test
	void iniciaSesionConCredencialesCorrectas() throws Exception {
		Comuna c = comuna("t6-login", true);
		Usuario u = usuario(c, "ana@mail.com", Rol.ADMINISTRATIVO, true);

		String respuesta = login("t6-login", "ana@mail.com", CLAVE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usuario.id").value(u.getId()))
				.andExpect(jsonPath("$.usuario.correo").value("ana@mail.com"))
				.andExpect(jsonPath("$.usuario.rol").value("ADMINISTRATIVO"))
				.andExpect(jsonPath("$.usuario.comunaSlug").value("t6-login"))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString(CLAVE))))
				.andReturn().getResponse().getContentAsString();

		DatosToken datos = jwt.leer(json.readTree(respuesta).get("token").asText());
		assertThat(datos.usuarioId()).isEqualTo(u.getId());
		assertThat(datos.rol()).isEqualTo(Rol.ADMINISTRATIVO);
		assertThat(datos.comunaId()).isEqualTo(c.getId());
	}

	@Test
	void elTokenDelLoginSirveParaEntrarARutasProtegidas() throws Exception {
		Comuna c = comuna("t6-token", true);
		Usuario u = usuario(c, "ana@mail.com", Rol.VECINO, true);

		String respuesta = login("t6-token", "ana@mail.com", CLAVE).andReturn().getResponse().getContentAsString();
		String token = json.readTree(respuesta).get("token").asText();

		mockMvc.perform(get("/api/prueba/yo").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().string(u.getId() + ":VECINO:" + c.getId()));
	}

	@Test
	void elCorreoNoDistingueMayusculasNiEspacios() throws Exception {
		comuna("t6-mayus", true);
		usuario(comunas.findBySlug("t6-mayus").orElseThrow(), "ana@mail.com", Rol.VECINO, true);

		login("t6-mayus", " ANA@Mail.com ", CLAVE).andExpect(status().isOk());
	}

	@Test
	void rechazaUnaClaveIncorrecta() throws Exception {
		Comuna c = comuna("t6-clave", true);
		usuario(c, "ana@mail.com", Rol.VECINO, true);

		login("t6-clave", "ana@mail.com", "otra-clave-99")
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
				.andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
	}

	@Test
	void unCorreoInexistenteDaExactamenteLaMismaRespuestaQueUnaClaveMala() throws Exception {
		Comuna c = comuna("t6-igual", true);
		usuario(c, "ana@mail.com", Rol.VECINO, true);

		String claveMala = login("t6-igual", "ana@mail.com", "otra-clave-99")
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
		String correoInexistente = login("t6-igual", "nadie@mail.com", CLAVE)
				.andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

		assertThat(correoInexistente).isEqualTo(claveMala);
	}

	@Test
	void rechazaUnUsuarioDeOtraComuna() throws Exception {
		Comuna a = comuna("t6-comuna-a", true);
		comuna("t6-comuna-b", true);
		usuario(a, "ana@mail.com", Rol.VECINO, true);

		login("t6-comuna-a", "ana@mail.com", CLAVE).andExpect(status().isOk());
		login("t6-comuna-b", "ana@mail.com", CLAVE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void rechazaUnUsuarioDesactivado() throws Exception {
		Comuna c = comuna("t6-desactivado", true);
		usuario(c, "ana@mail.com", Rol.VECINO, false);

		login("t6-desactivado", "ana@mail.com", CLAVE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.detail").value("Credenciales incorrectas"));
	}

	@Test
	void elSuperadminNoPuedeEntrarPorUnaComuna() throws Exception {
		comuna("t6-superadmin", true);
		usuarios.deleteAll(usuarios.findAll().stream().filter(x -> x.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		usuario(null, "root@mail.com", Rol.SUPERADMIN, true);

		login("t6-superadmin", "root@mail.com", CLAVE)
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}

	@Test
	void unaClaveMuyLargaDa401YNoUnError500() throws Exception {
		Comuna c = comuna("t6-larga", true);
		usuario(c, "ana@mail.com", Rol.VECINO, true);

		login("t6-larga", "ana@mail.com", "ñ".repeat(500)).andExpect(status().isUnauthorized());
	}

	@Test
	void loginEnUnaComunaInexistenteOInactivaDa404() throws Exception {
		comuna("t6-inactiva", false);

		login("t6-no-existe", "ana@mail.com", CLAVE).andExpect(status().isNotFound());
		login("t6-inactiva", "ana@mail.com", CLAVE).andExpect(status().isNotFound());
	}

	@Test
	void rechazaDatosMalFormadosConErroresPorCampo() throws Exception {
		comuna("t6-malos", true);

		login("t6-malos", "esto-no-es-un-correo", "").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.correo").exists())
				.andExpect(jsonPath("$.errores.clave").exists());
		mockMvc.perform(post("/api/comunas/t6-malos/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
	}
}
