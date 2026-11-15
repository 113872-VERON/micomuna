package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;

// Sin @Transactional a propósito: se comprueba que el servicio revierte solo, sin ayuda de la prueba.
@SpringBootTest
@AutoConfigureMockMvc
class AdminComunaAtomicidadTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@MockitoSpyBean
	private UsuarioRepository usuarios;

	@Test
	void siFallaLaCreacionDelAdministradorNoQuedaLaComuna() throws Exception {
		doThrow(new IllegalStateException("falla simulada")).when(usuarios)
				.saveAndFlush(argThat((Usuario u) -> u.getRol() == Rol.ADMIN_COMUNA));

		mockMvc.perform(post("/api/admin/comunas")
				.with(user("superadmin").roles("SUPERADMIN"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Atomica\",\"provincia\":\"CORDOBA\",\"slug\":\"t9-atomica\","
						+ "\"adminCorreo\":\"jefe@mail.com\",\"adminNombre\":\"Jefe\"}"))
				.andExpect(status().isInternalServerError());

		assertThat(comunas.existsBySlug("t9-atomica")).isFalse();
	}
}
