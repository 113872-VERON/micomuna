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

	// MC-15: el alta de un reclamo es un formulario multipart (como dice docs/api/openapi.yaml), no parámetros sueltos.
	@Test
	void elAltaDeUnReclamoDocumentaElFormularioMultipartYSusRespuestas() throws Exception {
		String alta = "$.paths['/api/reclamos'].post";
		String formulario = alta + ".requestBody.content['multipart/form-data']";
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath(alta + ".operationId").value("crearReclamo"))
				.andExpect(jsonPath(alta + ".tags[0]").value("Reclamos"))
				.andExpect(jsonPath(alta + ".parameters").doesNotExist())
				.andExpect(jsonPath(alta + ".requestBody.required").value(true))
				.andExpect(jsonPath(formulario + ".schema['$ref']").value("#/components/schemas/CrearReclamoForm"))
				.andExpect(jsonPath(formulario + ".encoding.fotos.contentType").value("image/jpeg, image/png"))
				.andExpect(jsonPath(alta + ".responses['201']").exists())
				.andExpect(jsonPath(alta + ".responses['400'].description").value(org.hamcrest.Matchers.containsString("FOTO_INVALIDA")))
				.andExpect(jsonPath(alta + ".responses['401']").exists())
				.andExpect(jsonPath(alta + ".responses['403']").exists())
				.andExpect(jsonPath(alta + ".responses['409'].description").value(org.hamcrest.Matchers.containsString("CONFLICTO")))
				.andExpect(jsonPath(alta + ".responses['429'].description").value(org.hamcrest.Matchers.containsString("DEMASIADOS_RECLAMOS")))
				.andExpect(jsonPath(alta + ".responses['503'].description").value(org.hamcrest.Matchers.containsString("OCUPADO")))
				.andExpect(jsonPath(alta + ".description").value(org.hamcrest.Matchers.containsString("2560 px")))
				.andExpect(jsonPath("$.components.schemas.CrearReclamoForm.required")
						.value(org.hamcrest.Matchers.containsInAnyOrder("tipo", "descripcion")))
				.andExpect(jsonPath("$.components.schemas.CrearReclamoForm.properties.fotos.maxItems").value(3))
				.andExpect(jsonPath("$.paths['/api/tipos-reclamo'].get.operationId").value("listarTiposReclamo"))
				.andExpect(jsonPath("$.paths['/api/tipos-reclamo'].get.responses['403']").exists());
	}

	@Test
	void losEndpointsPublicosNoDocumentan401() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/api/salud'].get.responses['401']").doesNotExist());
	}
}
