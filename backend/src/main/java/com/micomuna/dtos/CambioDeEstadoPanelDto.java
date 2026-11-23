package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Una fila del historial para el personal: a diferencia del vecino, ve quién lo hizo, la nota interna y el agente.
public record CambioDeEstadoPanelDto(
		@Schema(nullable = true) EstadoReclamo estadoAnterior,
		EstadoReclamo estado,
		Instant fecha,
		@Schema(nullable = true, description = "Quién hizo el cambio. Vacío si lo hizo el sistema; en el alta, el vecino") String usuarioNombre,
		@Schema(nullable = true, description = "Nota interna del cambio, si la hay. El vecino no la ve") String observacion,
		@Schema(nullable = true, description = "El agente que quedó asignado con este cambio (vacío si el cambio no tiene agente, o en filas anteriores a MC-19)") AgenteDto agente,
		@Schema(nullable = true, description = "Solo en reasignaciones: el agente que tenía el reclamo antes") AgenteDto agenteAnterior) {
}
