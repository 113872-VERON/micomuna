package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.fasterxml.jackson.databind.JsonNode;
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
class AuthControllerRegistroTests {

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

	private ResultActions registrar(String slug, String cuerpo) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + slug + "/registro")
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo));
	}

	private String cuerpo(String nombre, String correo, String clave) {
		return "{\"nombre\":\"" + nombre + "\",\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}";
	}

	@Test
	void registraUnVecinoYDevuelveToken() throws Exception {
		Comuna c = comuna("t5-registro", true);

		String respuesta = registrar("t5-registro", cuerpo("Ana", "ana@mail.com", "clave-segura-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.nombre").value("Ana"))
				.andExpect(jsonPath("$.usuario.correo").value("ana@mail.com"))
				.andExpect(jsonPath("$.usuario.rol").value("VECINO"))
				.andExpect(jsonPath("$.usuario.comunaSlug").value("t5-registro"))
				.andReturn().getResponse().getContentAsString();

		JsonNode raiz = json.readTree(respuesta);
		DatosToken datos = jwt.leer(raiz.get("token").asText());
		assertThat(datos.rol()).isEqualTo(Rol.VECINO);
		assertThat(datos.comunaId()).isEqualTo(c.getId());
		assertThat(datos.usuarioId()).isEqualTo(raiz.get("usuario").get("id").asLong());
	}

	@Test
	void laRespuestaNuncaTraeLaClave() throws Exception {
		comuna("t5-sin-clave", true);

		registrar("t5-sin-clave", cuerpo("Ana", "ana@mail.com", "clave-segura-1"))
				.andExpect(status().isCreated())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("clave-segura-1"))))
				.andExpect(jsonPath("$.usuario.claveHash").doesNotExist())
				.andExpect(jsonPath("$.usuario.clave").doesNotExist());
	}

	@Test
	void guardaLaClaveHasheada() throws Exception {
		Comuna c = comuna("t5-hash", true);

		registrar("t5-hash", cuerpo("Ana", "ana@mail.com", "clave-segura-1")).andExpect(status().isCreated());

		Usuario u = usuarios.findByComunaIdAndCorreo(c.getId(), "ana@mail.com").orElseThrow();
		assertThat(u.getClaveHash()).isNotEqualTo("clave-segura-1");
		assertThat(encoder.matches("clave-segura-1", u.getClaveHash())).isTrue();
	}

	@Test
	void normalizaElCorreo() throws Exception {
		Comuna c = comuna("t5-correo", true);

		registrar("t5-correo", cuerpo("Ana", " Ana@Mail.com ", "clave-segura-1"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.correo").value("ana@mail.com"));

		assertThat(usuarios.findByComunaIdAndCorreo(c.getId(), "ana@mail.com")).isPresent();
	}

	@Test
	void rechazaUnCorreoYaRegistradoEnLaComuna() throws Exception {
		comuna("t5-duplicado", true);
		registrar("t5-duplicado", cuerpo("Ana", "ana@mail.com", "clave-segura-1")).andExpect(status().isCreated());

		registrar("t5-duplicado", cuerpo("Otra Ana", "ANA@mail.com", "otra-clave-22"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("CONFLICTO"))
				.andExpect(jsonPath("$.detail").value("Ese correo ya está registrado en esta comuna"));
	}

	@Test
	void permiteElMismoCorreoEnOtraComuna() throws Exception {
		comuna("t5-comuna-a", true);
		comuna("t5-comuna-b", true);
		registrar("t5-comuna-a", cuerpo("Ana", "ana@mail.com", "clave-segura-1")).andExpect(status().isCreated());

		registrar("t5-comuna-b", cuerpo("Ana", "ana@mail.com", "clave-segura-1")).andExpect(status().isCreated());
	}

	@Test
	void rechazaUnaClaveDeMasDe72Caracteres() throws Exception {
		comuna("t5-larga", true);

		registrar("t5-larga", cuerpo("Ana", "ana@mail.com", "a".repeat(73)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.clave").exists());
	}

	@Test
	void aceptaUnaClaveDeExactamente72Caracteres() throws Exception {
		comuna("t5-justa", true);

		registrar("t5-justa", cuerpo("Ana", "ana@mail.com", "a".repeat(72))).andExpect(status().isCreated());
	}

	@Test
	void elLimiteDe72SeMideEnBytesYNoEnCaracteres() throws Exception {
		comuna("t5-bytes", true);

		// 40 letras "ñ" son 40 caracteres pero 80 bytes en UTF-8: BCrypt las truncaría en silencio.
		registrar("t5-bytes", cuerpo("Ana", "ana@mail.com", "ñ".repeat(40)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.clave").exists());
	}

	@Test
	void rechazaUnaClaveCorta() throws Exception {
		comuna("t5-corta", true);

		registrar("t5-corta", cuerpo("Ana", "ana@mail.com", "1234567"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.clave").exists());
	}

	@Test
	void rechazaUnCorreoInvalido() throws Exception {
		comuna("t5-invalido", true);

		registrar("t5-invalido", cuerpo("Ana", "esto-no-es-un-correo", "clave-segura-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.correo").exists());
	}

	@Test
	void rechazaUnNombreEnBlancoYDevuelveTodosLosErrores() throws Exception {
		comuna("t5-nombre", true);

		registrar("t5-nombre", cuerpo("   ", "esto-no-es-un-correo", "1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.nombre").exists())
				.andExpect(jsonPath("$.errores.correo").exists())
				.andExpect(jsonPath("$.errores.clave").exists());
	}

	@Test
	void rechazaUnCuerpoVacioOMalFormado() throws Exception {
		comuna("t5-malo", true);

		registrar("t5-malo", "{}").andExpect(status().isBadRequest());
		registrar("t5-malo", "esto no es json").andExpect(status().isBadRequest());
	}

	@Test
	void ignoraUnRolEnviadoEnElCuerpo() throws Exception {
		comuna("t5-rol", true);

		registrar("t5-rol", "{\"nombre\":\"Ana\",\"correo\":\"ana@mail.com\",\"clave\":\"clave-segura-1\","
				+ "\"rol\":\"SUPERADMIN\"}")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.usuario.rol").value("VECINO"));
	}

	@Test
	void registroEnUnaComunaInexistenteDa404() throws Exception {
		registrar("t5-no-existe", cuerpo("Ana", "ana@mail.com", "clave-segura-1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void registroEnUnaComunaInactivaDa404() throws Exception {
		comuna("t5-inactiva", false);

		registrar("t5-inactiva", cuerpo("Ana", "ana@mail.com", "clave-segura-1"))
				.andExpect(status().isNotFound());
	}
}
