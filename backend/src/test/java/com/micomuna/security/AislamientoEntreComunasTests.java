package com.micomuna.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;

// Criterio 4 de MC-13: un usuario nunca ve ni modifica datos de otra comuna, aunque cambie una URL o un identificador.
// Se prueba con los endpoints de RutaPorComunaDePrueba sobre USUARIO, que ya es una tabla por comuna.
// MC-14 y MC-15 repiten estas pruebas sobre los datos de negocio reales con el mismo patrón.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AislamientoEntreComunasTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	private Comuna comunaA;
	private Comuna comunaB;
	private Usuario vecinoA;
	private Usuario adminA;
	private Usuario vecinoB;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t13-aislamiento-a");
		comunaB = comuna("t13-aislamiento-b");
		vecinoA = usuario(comunaA, "vecino-a@mail.com", Rol.VECINO);
		adminA = usuario(comunaA, "admin-a@mail.com", Rol.ADMIN_COMUNA);
		vecinoB = usuario(comunaB, "vecino-b@mail.com", Rol.VECINO);
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash("hash");
		u.setNombre("Nombre original");
		u.setRol(rol);
		return usuarios.saveAndFlush(u);
	}

	private String bearer(Usuario u) {
		return "Bearer " + jwt.generar(u);
	}

	@Test
	void unUsuarioLeeSusDatosDentroDeSuComuna() throws Exception {
		mockMvc.perform(get("/api/prueba/usuarios/" + adminA.getId()).header("Authorization", bearer(vecinoA)))
				.andExpect(status().isOk())
				.andExpect(content().string("admin-a@mail.com"));
	}

	@Test
	void noPuedeLeerUnRecursoDeOtraComunaCambiandoElId() throws Exception {
		mockMvc.perform(get("/api/prueba/usuarios/" + vecinoB.getId()).header("Authorization", bearer(vecinoA)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void niElAdministradorDeUnaComunaLeeDatosDeOtra() throws Exception {
		mockMvc.perform(get("/api/prueba/usuarios/" + vecinoB.getId()).header("Authorization", bearer(adminA)))
				.andExpect(status().isNotFound());
	}

	@Test
	void unRecursoDeOtraComunaYUnoInexistenteRespondenIgual() throws Exception {
		String deOtraComuna = mockMvc.perform(get("/api/prueba/usuarios/" + vecinoB.getId())
				.header("Authorization", bearer(vecinoA))).andReturn().getResponse().getContentAsString();
		String inexistente = mockMvc.perform(get("/api/prueba/usuarios/999999999")
				.header("Authorization", bearer(vecinoA))).andReturn().getResponse().getContentAsString();

		// Solo cambia "instance" (la ruta pedida); el resto del cuerpo es idéntico.
		assertThat(sinInstance(deOtraComuna)).isEqualTo(sinInstance(inexistente));
	}

	private static String sinInstance(String cuerpo) {
		return cuerpo.replaceAll("\"instance\":\"[^\"]*\",?", "").replace(",}", "}");
	}

	@Test
	void noPuedeModificarUnRecursoDeOtraComuna() throws Exception {
		mockMvc.perform(patch("/api/prueba/usuarios/" + vecinoB.getId())
				.header("Authorization", bearer(adminA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Hackeado\"}"))
				.andExpect(status().isNotFound());

		usuarios.flush();
		assertThat(usuarios.findById(vecinoB.getId()).orElseThrow().getNombre()).isEqualTo("Nombre original");
	}

	@Test
	void unUsuarioModificaSusDatosDentroDeSuComuna() throws Exception {
		mockMvc.perform(patch("/api/prueba/usuarios/" + vecinoA.getId())
				.header("Authorization", bearer(adminA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Nombre nuevo\"}"))
				.andExpect(status().isOk())
				.andExpect(content().string("Nombre nuevo"));
	}

	@Test
	void laComunaSaleDelTokenYNoDelSlugDeLaUrl() throws Exception {
		mockMvc.perform(get("/api/prueba/" + comunaB.getSlug() + "/comuna-actual").header("Authorization", bearer(vecinoA)))
				.andExpect(status().isOk())
				.andExpect(content().string(String.valueOf(comunaA.getId())));
	}

	@Test
	void laComunaNoSaleDeUnParametroNiDelCuerpo() throws Exception {
		mockMvc.perform(get("/api/prueba/" + comunaA.getSlug() + "/comuna-actual")
				.param("idComuna", String.valueOf(comunaB.getId()))
				.header("Authorization", bearer(vecinoA)))
				.andExpect(content().string(String.valueOf(comunaA.getId())));

		// El cuerpo trae la comuna B: se ignora y el cambio igual se busca en la comuna del token.
		mockMvc.perform(patch("/api/prueba/usuarios/" + vecinoB.getId())
				.header("Authorization", bearer(vecinoA))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Hackeado\",\"idComuna\":" + comunaB.getId() + "}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void elSuperadminNoPerteneceAUnaComunaYNoEntraAEsosDatos() throws Exception {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario superadmin = usuario(null, "t13-root@mail.com", Rol.SUPERADMIN);

		mockMvc.perform(get("/api/prueba/usuarios/" + vecinoA.getId()).header("Authorization", bearer(superadmin)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
	}

	@Test
	void sinTokenNoHayAcceso() throws Exception {
		mockMvc.perform(get("/api/prueba/usuarios/" + vecinoA.getId())).andExpect(status().isUnauthorized());
	}

	@Test
	void siElUsuarioCambiaDeComunaElTokenViejoYaNoVeLaAnterior() throws Exception {
		String tokenViejo = bearer(vecinoA);
		vecinoA.setComuna(comunaB);
		usuarios.saveAndFlush(vecinoA);

		// La comuna se lee de la base en cada petición: el token viejo ahora actúa en la comuna B.
		mockMvc.perform(get("/api/prueba/usuarios/" + adminA.getId()).header("Authorization", tokenViejo))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/prueba/usuarios/" + vecinoB.getId()).header("Authorization", tokenViejo))
				.andExpect(status().isOk());
	}
}
