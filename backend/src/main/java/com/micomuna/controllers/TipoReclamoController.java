package com.micomuna.controllers;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.TipoReclamoDto;
import com.micomuna.services.ReclamoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Reclamos", description = "Reclamos de los vecinos de una comuna")
@RestController
@RequestMapping("/api/tipos-reclamo")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TipoReclamoController {

	private final ReclamoService reclamoService;

	// Cualquier usuario de una comuna: el vecino los usa para reportar y el personal, para filtrar.
	// El superadmin no pertenece a ninguna comuna.
	@Operation(operationId = "listarTiposReclamo", summary = "Tipos de problema", description = "Los seis tipos de problema, iguales para todas las comunas, en el orden en que se muestran, con el consejo para sacar la foto de cada uno. Para cualquier usuario de una comuna (no el superadmin).")
	@GetMapping
	@PreAuthorize("hasAnyRole('VECINO', 'AGENTE', 'ADMINISTRATIVO', 'ADMIN_COMUNA')")
	public List<TipoReclamoDto> listar() {
		return reclamoService.listarTipos();
	}
}
