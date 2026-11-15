package com.micomuna.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;
import com.micomuna.repositories.ComunaRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ComunaControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	private Comuna guardar(String slug, boolean activa) {
		Comuna c = new Comuna();
		c.setNombre("Salsipuedes");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		c.setActiva(activa);
		c.setDepartamento("Departamento Colón");
		c.setColor(ColorComuna.BOSQUE);
		return comunas.saveAndFlush(c);
	}

	@Test
	void devuelveLosDatosDeUnaComunaActiva() throws Exception {
		guardar("t3c-salsipuedes-cordoba", true);

		mockMvc.perform(get("/api/comunas/t3c-salsipuedes-cordoba"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Salsipuedes"))
				.andExpect(jsonPath("$.slug").value("t3c-salsipuedes-cordoba"))
				.andExpect(jsonPath("$.provincia").value("Córdoba"))
				.andExpect(jsonPath("$.departamento").value("Departamento Colón"))
				.andExpect(jsonPath("$.color").value("BOSQUE"))
				.andExpect(jsonPath("$.tieneLogo").value(false));
	}

	@Test
	void indicaSiLaComunaTieneLogoSinDevolverLaImagen() throws Exception {
		Comuna c = guardar("t3c-con-logo", true);
		c.setLogo(new byte[] { 1, 2, 3 });
		c.setLogoTipo("image/png");
		comunas.saveAndFlush(c);

		mockMvc.perform(get("/api/comunas/t3c-con-logo"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneLogo").value(true))
				.andExpect(jsonPath("$.logo").doesNotExist());
	}

	@Test
	void devuelve404SiElSlugNoExiste() throws Exception {
		mockMvc.perform(get("/api/comunas/t3c-no-existe"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void devuelve404SiLaComunaEstaInactiva() throws Exception {
		guardar("t3c-inactiva", false);

		mockMvc.perform(get("/api/comunas/t3c-inactiva"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	@Test
	void devuelve404ConUnSlugConCaracteresRaros() throws Exception {
		guardar("t3c-salsipuedes-cordoba", true);

		for (String slug : new String[] { "T3C-Salsipuedes-Cordoba", "a_b", "-a", "a--b" }) {
			mockMvc.perform(get("/api/comunas/" + slug))
					.andExpect(status().isNotFound())
					.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
		}
	}

	// Estas URLs las frena el firewall de Spring Security antes de llegar a la aplicación.
	@Test
	void unSlugPeligrosoNuncaDevuelveDatos() throws Exception {
		for (String slug : new String[] { "..%2F..%2Fetc", "%3Cscript%3E", "a%20b" }) {
			mockMvc.perform(get("/api/comunas/" + slug))
					.andExpect(status().is4xxClientError());
		}
	}

	@Test
	void elErrorNoMuestraDetallesInternos() throws Exception {
		mockMvc.perform(get("/api/comunas/t3c-no-existe"))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("Exception"))))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("com.micomuna"))));
	}
}
