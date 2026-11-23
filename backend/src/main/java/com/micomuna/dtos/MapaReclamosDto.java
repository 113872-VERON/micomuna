package com.micomuna.dtos;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record MapaReclamosDto(
		List<PuntoReclamoDto> puntos,
		@Schema(description = "true si había más de 1000 y se devolvieron solo los 1000 más nuevos") boolean recortado) {
}
