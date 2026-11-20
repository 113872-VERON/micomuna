package com.micomuna.configs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void apiDocsListaElEndpointDeSalud() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/salud']").exists())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
	}

	// MC-13: los endpoints protegidos documentan 401 y, si exigen un rol, también 403 ACCESO_DENEGADO.
	@Test
	void losEndpointsDeAdminDocumentan401Y403() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/admin/comunas'].get.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/comunas'].get.responses['403'].description")
						.value(org.hamcrest.Matchers.containsString("ACCESO_DENEGADO")))
				.andExpect(jsonPath("$.paths['/api/admin/comunas'].post.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/api/admin/comunas/{id}'].patch.responses['403']").exists());
	}

	@Test
	void losEndpointsDeMiCuentaDocumentan401PeroNoUn403PorRol() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/auth/cambiar-clave'].post.responses['401']").exists())
				.andExpect(jsonPath("$.paths['/api/auth/cambiar-clave'].post.responses['403']").doesNotExist());
	}

	// Los controladores de src/test no son parte de la API: no tienen que colarse en la documentación
	// (ni en docs/api/openapi.yaml si se genera con las pruebas cargadas). Ver src/test/resources/config/application.properties.
	@Test
	void losEndpointsDePruebaNoAparecenEnLaDocumentacion() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/api/prueba/yo']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/prueba/usuarios/{id}']").doesNotExist())
				.andExpect(jsonPath("$.paths['/api/prueba/{slug}/comuna-actual']").doesNotExist());
	}

	@Test
	void losEndpointsPublicosNoDocumentan401() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/salud'].get.responses['401']").doesNotExist());
	}
}
