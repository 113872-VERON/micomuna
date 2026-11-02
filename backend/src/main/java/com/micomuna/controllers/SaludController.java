package com.micomuna.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/salud")
public class SaludController {

	@GetMapping
	public Map<String, String> estado() {
		return Map.of("estado", "ok");
	}
}
