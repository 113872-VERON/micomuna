package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record AgenteConCargaDto(
		Long id,
		String nombre,
		@Schema(description = "Reclamos de la comuna que tiene en ASIGNADO o EN_REPARACION") int enCurso) {
}
