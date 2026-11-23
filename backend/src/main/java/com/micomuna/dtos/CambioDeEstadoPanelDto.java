package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Una fila del historial para el personal: a diferencia del vecino, ve quién lo hizo y la nota interna.
public record CambioDeEstadoPanelDto(
		@Schema(nullable = true) EstadoReclamo estadoAnterior,
		EstadoReclamo estado,
		Instant fecha,
		@Schema(nullable = true, description = "Quién hizo el cambio. Vacío si lo hizo el sistema; en el alta, el vecino") String usuarioNombre,
		@Schema(nullable = true, description = "Nota interna del cambio, si la hay. El vecino no la ve") String observacion) {
}
