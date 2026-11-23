package com.micomuna.dtos;

import java.time.Instant;
import java.util.Map;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

public record ResumenPanelDto(
		@Schema(description = "Cantidad de reclamos en cada estado (los cinco, con 0 si no hay)") Map<EstadoReclamo, Long> porEstado,
		long total,
		@Schema(description = "Reclamos en RECIBIDO de la comuna, sin filtros") long sinAsignar,
		@Schema(nullable = true, description = "Fecha de creación del reclamo sin asignar más viejo") Instant sinAsignarMasAntiguo) {
}
