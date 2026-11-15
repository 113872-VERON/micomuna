package com.micomuna.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ErroresHttpYRutasAdminTests {

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

	private Comuna comuna;

	@BeforeEach
	void preparar() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comuna = new Comuna();
		comuna.setNombre("Comuna de prueba");
		comuna.setProvincia(Provincia.CORDOBA);
		comuna.setSlug("t11-http");
		comunas.saveAndFlush(comuna);
	}

	private String token(Comuna deLaComuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(deLaComuna);
		u.setCorreo(correo);
		u.setClaveHash(encoder.encode("clave-segura-1"));
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setActivo(true);
		return jwt.generar(usuarios.saveAndFlush(u));
	}

	// ---- Errores HTTP que no son del servidor

	@Test
	void unMetodoNoSoportadoDa405YNoUn500() throws Exception {
		mockMvc.perform(post("/api/salud"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
	}

	@Test
	void unTipoDeContenidoNoSoportadoDa415YNoUn500() throws Exception {
		mockMvc.perform(post("/api/comunas/t11-http/login").contentType(MediaType.TEXT_PLAIN).content("hola"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.codigo").value("TIPO_NO_SOPORTADO"));
	}

	// ---- Todo /api/admin/** es solo del superadmin (además del @PreAuthorize de cada controlador)

	@Test
	void unaRutaAdminSinTokenDa401() throws Exception {
		mockMvc.perform(get("/api/admin/lo-que-sea")).andExpect(status().isUnauthorized());
	}

	@Test
	void unaRutaAdminDeUnaRutaQueNoExisteEsProhibidaParaQuienNoEsSuperadmin() throws Exception {
		String vecino = token(comuna, "vecino@mail.com", Rol.VECINO);
		String adminComuna = token(comuna, "jefe@mail.com", Rol.ADMIN_COMUNA);

		mockMvc.perform(get("/api/admin/lo-que-sea").header("Authorization", "Bearer " + vecino))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/lo-que-sea").header("Authorization", "Bearer " + adminComuna))
				.andExpect(status().isForbidden());
	}

	@Test
	void elSuperadminSiPasaLaReglaYRecibe404SiLaRutaNoExiste() throws Exception {
		String superadmin = token(null, "root@mail.com", Rol.SUPERADMIN);

		mockMvc.perform(get("/api/admin/lo-que-sea").header("Authorization", "Bearer " + superadmin))
				.andExpect(status().isNotFound());
	}

	@Test
	void elLoginDelSuperadminSigueSiendoPublico() throws Exception {
		mockMvc.perform(post("/api/admin/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"nadie@mail.com\",\"clave\":\"clave-segura-1\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
	}
}
