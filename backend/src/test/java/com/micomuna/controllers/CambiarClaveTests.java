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
class CambiarClaveTests {

	private static final String ACTUAL = "clave-actual-1";
	private static final String NUEVA = "clave-nueva-22";

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

	private Usuario vecino(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		c = comunas.saveAndFlush(c);
		return usuario(c, "ana@mail.com", Rol.VECINO);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash(encoder.encode(ACTUAL));
		u.setNombre("Ana");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private ResultActions cambiar(Usuario u, String actual, String nueva) throws Exception {
		return mockMvc.perform(post("/api/auth/cambiar-clave")
				.header("Authorization", "Bearer " + jwt.generar(u))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"claveActual\":\"" + actual + "\",\"claveNueva\":\"" + nueva + "\"}"));
	}

	private ResultActions login(String slug, String correo, String clave) throws Exception {
		return mockMvc.perform(post("/api/comunas/" + slug + "/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}"));
	}

	@Test
	void cambiaLaClaveConLaActualCorrecta() throws Exception {
		Usuario u = vecino("t8-cambia");

		cambiar(u, ACTUAL, NUEVA).andExpect(status().isNoContent()).andExpect(content().string(""));

		login("t8-cambia", "ana@mail.com", NUEVA).andExpect(status().isOk());
		login("t8-cambia", "ana@mail.com", ACTUAL).andExpect(status().isUnauthorized());
	}

	@Test
	void guardaLaClaveNuevaHasheada() throws Exception {
		Usuario u = vecino("t8-hash");

		cambiar(u, ACTUAL, NUEVA).andExpect(status().isNoContent());

		Usuario guardado = usuarios.findById(u.getId()).orElseThrow();
		assertThat(guardado.getClaveHash()).isNotEqualTo(NUEVA);
		assertThat(encoder.matches(NUEVA, guardado.getClaveHash())).isTrue();
	}

	@Test
	void rechazaUnaClaveActualIncorrecta() throws Exception {
		Usuario u = vecino("t8-incorrecta");

		cambiar(u, "no-es-la-actual", NUEVA)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CLAVE_ACTUAL_INCORRECTA"))
				.andExpect(jsonPath("$.detail").value("La contraseña actual no es correcta"));

		login("t8-incorrecta", "ana@mail.com", ACTUAL).andExpect(status().isOk());
	}

	@Test
	void rechazaUnaClaveActualMuyLargaSinError500() throws Exception {
		Usuario u = vecino("t8-actual-larga");

		cambiar(u, "ñ".repeat(500), NUEVA)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CLAVE_ACTUAL_INCORRECTA"));
	}

	@Test
	void rechazaUnaClaveNuevaDeMasDe72Bytes() throws Exception {
		Usuario u = vecino("t8-larga");

		cambiar(u, ACTUAL, "a".repeat(73))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.claveNueva").exists());
		cambiar(u, ACTUAL, "ñ".repeat(40))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.claveNueva").exists());
	}

	@Test
	void rechazaUnaClaveNuevaCorta() throws Exception {
		Usuario u = vecino("t8-corta");

		cambiar(u, ACTUAL, "1234567")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.claveNueva").exists());
	}

	@Test
	void rechazaUnaClaveNuevaIgualALaActual() throws Exception {
		Usuario u = vecino("t8-igual");

		cambiar(u, ACTUAL, ACTUAL)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("CLAVE_NUEVA_IGUAL"))
				.andExpect(jsonPath("$.detail").value("La contraseña nueva tiene que ser distinta de la actual"));
	}

	@Test
	void rechazaCamposVacios() throws Exception {
		Usuario u = vecino("t8-vacios");

		cambiar(u, "", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.claveActual").exists())
				.andExpect(jsonPath("$.errores.claveNueva").exists());
	}

	@Test
	void sinTokenDevuelve401() throws Exception {
		mockMvc.perform(post("/api/auth/cambiar-clave")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"claveActual\":\"" + ACTUAL + "\",\"claveNueva\":\"" + NUEVA + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unTokenDeUnUsuarioDesactivadoDa401() throws Exception {
		Usuario u = vecino("t8-desactivado");
		String token = jwt.generar(u);
		u.setActivo(false);
		usuarios.saveAndFlush(u);

		mockMvc.perform(post("/api/auth/cambiar-clave")
				.header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"claveActual\":\"" + ACTUAL + "\",\"claveNueva\":\"" + NUEVA + "\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void elSuperadminTambienPuedeCambiarSuClave() throws Exception {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(x -> x.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario u = usuario(null, "root@mail.com", Rol.SUPERADMIN);

		cambiar(u, ACTUAL, NUEVA).andExpect(status().isNoContent());

		mockMvc.perform(post("/api/admin/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"root@mail.com\",\"clave\":\"" + NUEVA + "\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void unUsuarioSoloPuedeCambiarSuPropiaClave() throws Exception {
		Usuario ana = vecino("t8-propia");
		Usuario otra = usuario(ana.getComuna(), "otra@mail.com", Rol.VECINO);

		cambiar(ana, ACTUAL, NUEVA).andExpect(status().isNoContent());

		// La clave de la otra persona no se tocó: el endpoint no recibe un id, usa siempre al del token.
		assertThat(encoder.matches(ACTUAL, usuarios.findById(otra.getId()).orElseThrow().getClaveHash())).isTrue();
	}
}
