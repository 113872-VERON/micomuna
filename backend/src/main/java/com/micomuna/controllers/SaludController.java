package com.micomuna.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Salud", description = "Comprobación de que el backend responde")
@RestController
@RequestMapping("/api/salud")
public class SaludController {

	@Operation(summary = "Estado del backend")
	@GetMapping
	public Map<String, String> estado() {
		return Map.of("estado", "ok");
	}
}
