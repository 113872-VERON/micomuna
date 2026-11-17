package com.micomuna.controllers;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.ComunaDto;
import com.micomuna.entities.Comuna;
import com.micomuna.services.ComunaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Comunas", description = "Consulta pública de una comuna por su enlace")
@RestController
@RequestMapping("/api/comunas")
@RequiredArgsConstructor
public class ComunaController {

	private final ComunaService comunaService;

	@Operation(summary = "Datos públicos de una comuna", description = "Sirve para armar la cabecera de las pantallas de ingreso. No requiere sesión.")
	@ApiResponse(responseCode = "404", description = "La comuna no existe o está inactiva")
	@GetMapping("/{slug}")
	public ComunaDto buscar(@PathVariable String slug) {
		return ComunaDto.desde(comunaService.buscarActivaPorSlug(slug));
	}

	@Operation(summary = "Logo de una comuna", description = "Imagen PNG o JPEG. No requiere sesión.")
	@ApiResponse(responseCode = "404", description = "La comuna no existe, está inactiva o no tiene logo")
	@GetMapping("/{slug}/logo")
	public ResponseEntity<byte[]> logo(@PathVariable String slug) {
		Comuna comuna = comunaService.buscarActivaConLogo(slug);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(comuna.getLogoTipo()))
				.cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
				.body(comuna.getLogo());
	}
}
