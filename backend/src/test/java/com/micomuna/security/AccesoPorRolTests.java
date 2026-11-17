package com.micomuna.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;

// Criterios 2 y 3 de MC-13 (subtarea MC-53): el token y el rol se validan en cada endpoint protegido
// y quien no tiene el rol adecuado recibe 403 ACCESO_DENEGADO.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccesoPorRolTests {

	private static final Set<Rol> NO_SUPERADMIN = EnumSet.complementOf(EnumSet.of(Rol.SUPERADMIN));

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	@Value("${jwt.secret}")
	private String secreto;

	private Comuna comuna;

	@BeforeEach
	void preparar() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comuna = new Comuna();
		comuna.setNombre("Comuna de prueba");
		comuna.setProvincia(Provincia.CORDOBA);
		comuna.setSlug("t13-acceso-rol");
		comunas.saveAndFlush(comuna);
	}

	private Usuario usuario(Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(rol == Rol.SUPERADMIN ? null : comuna);
		u.setCorreo(rol.name().toLowerCase() + "@mail.com");
		u.setClaveHash("hash");
		u.setNombre("Nombre");
		u.setRol(rol);
		return usuarios.saveAndFlush(u);
	}

	private String bearer(Rol rol) {
		return "Bearer " + jwt.generar(usuario(rol));
	}

	private MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder pedido) {
		return pedido.contentType(MediaType.APPLICATION_JSON).content("{}");
	}

	// ---- Endpoints solo del superadmin: los otros cuatro roles reciben 403 ACCESO_DENEGADO

	private MockHttpServletRequestBuilder[] endpointsDelSuperadmin() {
		return new MockHttpServletRequestBuilder[] {
				get("/api/admin/comunas"),
				conCuerpo(post("/api/admin/comunas")),
				conCuerpo(patch("/api/admin/comunas/1")),
				post("/api/admin/comunas/1/administrador/reenviar-invitacion"),
		};
	}

	@Test
	void ningunRolQueNoSeaSuperadminEntraALosEndpointsDelSuperadmin() throws Exception {
		for (Rol rol : NO_SUPERADMIN) {
			String token = bearer(rol);
			for (MockHttpServletRequestBuilder endpoint : endpointsDelSuperadmin()) {
				mockMvc.perform(endpoint.header("Authorization", token))
						.andExpect(status().isForbidden())
						.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			}
		}
	}

	@Test
	void elSuperadminSiPasaLaReglaDeRol() throws Exception {
		String token = bearer(Rol.SUPERADMIN);

		mockMvc.perform(get("/api/admin/comunas").header("Authorization", token)).andExpect(status().isOk());
	}

	@Test
	void unaRutaDeAdminQueNoExisteTampocoSeMuestraAQuienNoEsSuperadmin() throws Exception {
		for (Rol rol : NO_SUPERADMIN) {
			mockMvc.perform(delete("/api/admin/lo-que-sea").header("Authorization", bearer(rol)))
					.andExpect(status().isForbidden());
		}
	}

	// ---- Endpoints de cualquier usuario con sesión: los cinco roles pasan, sin sesión es 401

	@Test
	void cualquierRolConSesionPasaAlosEndpointsDeMiCuenta() throws Exception {
		for (Rol rol : Rol.values()) {
			// Cuerpo vacío: pasa la seguridad y la validación responde 400, nunca 401 ni 403.
			mockMvc.perform(conCuerpo(post("/api/auth/cambiar-clave")).header("Authorization", bearer(rol)))
					.andExpect(status().isBadRequest());
		}
	}

	@Test
	void sinTokenLosEndpointsProtegidosResponden401() throws Exception {
		for (MockHttpServletRequestBuilder endpoint : endpointsDelSuperadmin()) {
			mockMvc.perform(endpoint).andExpect(status().isUnauthorized());
		}
		mockMvc.perform(conCuerpo(post("/api/auth/cambiar-clave"))).andExpect(status().isUnauthorized());
	}

	// ---- El token se valida en cada petición

	@Test
	void unTokenVencidoSeRechazaEnElFiltro() throws Exception {
		Usuario u = usuario(Rol.VECINO);
		String vencido = new JwtService(secreto, Duration.ofSeconds(-1)).generar(u);

		mockMvc.perform(get("/api/prueba/yo").header("Authorization", "Bearer " + vencido))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unTokenFirmadoConOtraClaveSeRechaza() throws Exception {
		Usuario u = usuario(Rol.VECINO);
		String ajeno = new JwtService("otro-secreto-distinto-tambien-de-mas-de-32-caracteres", Duration.ofHours(1))
				.generar(u);

		mockMvc.perform(get("/api/prueba/yo").header("Authorization", "Bearer " + ajeno))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sinElPrefijoBearerNoHayAutenticacion() throws Exception {
		Usuario u = usuario(Rol.VECINO);

		mockMvc.perform(get("/api/prueba/yo").header("Authorization", jwt.generar(u)))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unCambioDeRolSeAplicaDeInmediatoAunqueElTokenSigaVigente() throws Exception {
		Usuario u = usuario(Rol.ADMINISTRATIVO);
		String token = "Bearer " + jwt.generar(u);
		mockMvc.perform(get("/api/prueba/yo").header("Authorization", token))
				.andExpect(content().string(u.getId() + ":ADMINISTRATIVO:" + comuna.getId()));

		u.setRol(Rol.AGENTE);
		usuarios.saveAndFlush(u);

		// El rol del token es solo el del momento del login: manda el que está en la base.
		mockMvc.perform(get("/api/prueba/yo").header("Authorization", token))
				.andExpect(content().string(u.getId() + ":AGENTE:" + comuna.getId()));
	}

	@Test
	void unUsuarioDegradadoPierdeElAccesoAlPanelAunqueElTokenSigaVigente() throws Exception {
		Usuario u = usuario(Rol.SUPERADMIN);
		String token = "Bearer " + jwt.generar(u);
		mockMvc.perform(get("/api/admin/comunas").header("Authorization", token)).andExpect(status().isOk());

		u.setRol(Rol.VECINO);
		u.setComuna(comuna);
		usuarios.saveAndFlush(u);

		mockMvc.perform(get("/api/admin/comunas").header("Authorization", token)).andExpect(status().isForbidden());
		assertThat(usuarios.findById(u.getId()).orElseThrow().getRol()).isEqualTo(Rol.VECINO);
	}
}
