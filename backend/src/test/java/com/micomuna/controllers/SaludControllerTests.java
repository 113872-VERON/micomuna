package com.micomuna.controllers;

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
class SaludControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void saludEsPublicoYResponde() throws Exception {
		mockMvc.perform(get("/api/salud"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ok"));
	}

	@Test
	void otrosEndpointsRequierenAutenticacion() throws Exception {
		mockMvc.perform(get("/api/reclamos"))
				.andExpect(status().isUnauthorized());
	}
}
