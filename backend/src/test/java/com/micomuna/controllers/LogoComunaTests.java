package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
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
class LogoComunaTests {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3 };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3 };

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
	private String tokenSuperadmin;

	@BeforeEach
	void preparar() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comuna = comuna("t12-logo");
		tokenSuperadmin = token(null, "root@mail.com", Rol.SUPERADMIN);
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
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

	private ResultActions subir(Long id, String token, String tipoDeclarado, byte[] bytes) throws Exception {
		var pedido = multipart(HttpMethod.PUT, "/api/admin/comunas/" + id + "/logo")
				.file(new MockMultipartFile("archivo", "logo", tipoDeclarado, bytes));
		return mockMvc.perform(token == null ? pedido : pedido.header("Authorization", "Bearer " + token));
	}

	@Test
	void elSuperadminSubeUnPngYSeSirveSinSesion() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneLogo").value(true))
				.andExpect(jsonPath("$.logo").doesNotExist());

		mockMvc.perform(get("/api/comunas/t12-logo/logo"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(content().bytes(PNG));
	}

	@Test
	void aceptaJpegYReemplazaElLogoAnterior() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG).andExpect(status().isOk());
		subir(comuna.getId(), tokenSuperadmin, "image/jpeg", JPEG).andExpect(status().isOk());

		mockMvc.perform(get("/api/comunas/t12-logo/logo"))
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(content().bytes(JPEG));
	}

	@Test
	void elTipoSeDecidePorElContenidoYNoPorLoQueDiceElCliente() throws Exception {
		// Un JPEG que se declara como PNG se guarda y se sirve como JPEG.
		subir(comuna.getId(), tokenSuperadmin, "image/png", JPEG).andExpect(status().isOk());

		mockMvc.perform(get("/api/comunas/t12-logo/logo")).andExpect(header().string("Content-Type", "image/jpeg"));
	}

	@Test
	void rechazaUnSvgAunqueSeDeclareComoPng() throws Exception {
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();

		subir(comuna.getId(), tokenSuperadmin, "image/png", svg)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LOGO_INVALIDO"));
		subir(comuna.getId(), tokenSuperadmin, "image/svg+xml", svg).andExpect(status().isBadRequest());
		assertThat(comunas.findById(comuna.getId()).orElseThrow().getLogo()).isNull();
	}

	@Test
	void rechazaUnArchivoVacio() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", new byte[0])
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LOGO_INVALIDO"));
	}

	@Test
	void rechazaUnLogoDeMasDe512KB() throws Exception {
		byte[] grande = Arrays.copyOf(PNG, 512 * 1024 + 1);

		subir(comuna.getId(), tokenSuperadmin, "image/png", grande)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LOGO_INVALIDO"));
	}

	@Test
	void aceptaUnLogoDeExactamente512KB() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", Arrays.copyOf(PNG, 512 * 1024))
				.andExpect(status().isOk());
	}

	@Test
	void sinElArchivoDa400() throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/admin/comunas/" + comuna.getId() + "/logo")
				.header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
	}

	@Test
	void unaComunaQueNoExisteDa404() throws Exception {
		subir(999999L, tokenSuperadmin, "image/png", PNG).andExpect(status().isNotFound());
	}

	@Test
	void soloElSuperadminPuedeSubirElLogo() throws Exception {
		String vecino = token(comuna, "vecino@mail.com", Rol.VECINO);
		String admin = token(comuna, "jefe@mail.com", Rol.ADMIN_COMUNA);

		subir(comuna.getId(), null, "image/png", PNG).andExpect(status().isUnauthorized());
		subir(comuna.getId(), vecino, "image/png", PNG).andExpect(status().isForbidden());
		subir(comuna.getId(), admin, "image/png", PNG).andExpect(status().isForbidden());
	}

	@Test
	void sinLogoLaConsultaPublicaDa404() throws Exception {
		mockMvc.perform(get("/api/comunas/t12-logo/logo")).andExpect(status().isNotFound());
	}

	@Test
	void unaComunaInactivaNoSirveSuLogo() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG).andExpect(status().isOk());
		comuna.setActiva(false);
		comunas.saveAndFlush(comuna);

		mockMvc.perform(get("/api/comunas/t12-logo/logo")).andExpect(status().isNotFound());
	}

	@Test
	void unEnlaceInexistenteOInvalidoDa404() throws Exception {
		mockMvc.perform(get("/api/comunas/no-existe/logo")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/comunas/NO_VALIDO!/logo")).andExpect(status().isNotFound());
	}
}
