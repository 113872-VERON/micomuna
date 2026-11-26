package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Un reclamo cercano que podría ser el mismo problema (MC-26). Solo lo ve el personal.
public record PosibleDuplicadoDto(
		Integer numero,
		EstadoReclamo estado,
		@Schema(description = "Distancia aproximada a este reclamo, redondeada al metro", minimum = "0", maximum = "100") int distanciaMetros,
		Instant creadoEn,
		@Schema(nullable = true) String direccion,
		@Schema(description = "Lo que escribió el vecino, para comparar (solo lo ve el personal)") String descripcion) {
}
