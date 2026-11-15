package com.micomuna.configs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

// Con el perfil prod la documentación de la API no se publica ni queda ninguna ruta de Swagger abierta.
// Se le dan los datos de la base de pruebas por propiedades porque la conexión de prod sale de variables de entorno.
@SpringBootTest(properties = {
		"spring.config.import=optional:file:../db/.env[.properties]",
		"spring.datasource.url=jdbc:oracle:thin:@//localhost:${ORACLE_PORT:1522}/FREEPDB1",
		"spring.datasource.username=${APP_USER:micomuna}",
		"spring.datasource.password=${APP_USER_PASSWORD}" })
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class PerfilProdTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void laDocumentacionNoEstaAbiertaEnProduccion() throws Exception {
		mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
	}

	@Test
	void elRestoDeLaApiSigueFuncionando() throws Exception {
		mockMvc.perform(get("/api/salud")).andExpect(status().isOk());
	}
}
