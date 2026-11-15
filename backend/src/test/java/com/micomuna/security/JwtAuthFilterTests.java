package com.micomuna.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class JwtAuthFilterTests {

	private static final String RUTA = "/api/prueba/yo";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

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
		u.setClaveHash("hash");
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private String bearer(Usuario u) {
		return "Bearer " + jwt.generar(u);
	}

	@Test
	void aceptaUnTokenValido() throws Exception {
		Comuna c = comuna("t4-valido", true);
		Usuario u = usuario(c, "ana@mail.com", Rol.VECINO, true);

		mockMvc.perform(get(RUTA).header("Authorization", bearer(u)))
				.andExpect(status().isOk())
				.andExpect(content().string(u.getId() + ":VECINO:" + c.getId()));
	}

	@Test
	void aceptaAlSuperadminSinComuna() throws Exception {
		// Solo puede haber un superadmin: se borra el que exista (la transacción de la prueba se deshace al terminar).
		usuarios.deleteAll(usuarios.findAll().stream().filter(x -> x.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario u = usuario(null, "t4-root@mail.com", Rol.SUPERADMIN, true);

		mockMvc.perform(get(RUTA).header("Authorization", bearer(u)))
				.andExpect(status().isOk())
				.andExpect(content().string(u.getId() + ":SUPERADMIN:null"));
	}

	@Test
	void rechazaConUnUsuarioDesactivado() throws Exception {
		Usuario u = usuario(comuna("t4-usuario-off", true), "ana@mail.com", Rol.VECINO, false);

		mockMvc.perform(get(RUTA).header("Authorization", bearer(u)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rechazaConUnaComunaDesactivada() throws Exception {
		Usuario u = usuario(comuna("t4-comuna-off", false), "ana@mail.com", Rol.VECINO, true);

		mockMvc.perform(get(RUTA).header("Authorization", bearer(u)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rechazaUnUsuarioQueYaNoExiste() throws Exception {
		Usuario u = usuario(comuna("t4-borrado", true), "ana@mail.com", Rol.VECINO, true);
		String token = bearer(u);
		usuarios.delete(u);
		usuarios.flush();

		mockMvc.perform(get(RUTA).header("Authorization", token))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void rechazaUnTokenInvalido() throws Exception {
		mockMvc.perform(get(RUTA).header("Authorization", "Bearer esto.no.sirve"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sinTokenDevuelve401() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
	}

	@Test
	void unTokenInvalidoNoDaAccesoAUnaRutaPublica() throws Exception {
		mockMvc.perform(get("/api/salud").header("Authorization", "Bearer esto.no.sirve"))
				.andExpect(status().isUnauthorized());
	}
}
