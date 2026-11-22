package com.micomuna.controllers;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.services.ComunaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

// Datos de la comuna del usuario con sesión. La comuna sale siempre del token (ComunaActual), nunca de la URL.
@Tag(name = "Reclamos", description = "Reclamos de los vecinos de una comuna")
@RestController
@RequestMapping("/api/mi-comuna")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MiComunaController {

	private final ComunaService comunaService;

	@Operation(operationId = "limiteDeMiComuna", summary = "Límite geográfico de mi comuna", description = "Para cualquier usuario de una comuna (no el superadmin). La comuna sale del token. Devuelve la figura GeoJSON (Polygon o MultiPolygon) para dibujarla en el mapa, o 204 si la comuna no tiene límite cargado.")
	@ApiResponse(responseCode = "200", description = "La figura GeoJSON del límite", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(type = "object")))
	@ApiResponse(responseCode = "204", description = "La comuna no tiene límite cargado", content = @Content)
	@GetMapping("/limite")
	@PreAuthorize("hasAnyRole('VECINO', 'AGENTE', 'ADMINISTRATIVO', 'ADMIN_COMUNA')")
	public ResponseEntity<String> limite() {
		// La figura se guardó ya normalizada: se devuelve el texto tal cual, sin leerlo ni armarlo de nuevo.
		return comunaService.limiteDeMiComuna()
				.map(figura -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(figura))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
