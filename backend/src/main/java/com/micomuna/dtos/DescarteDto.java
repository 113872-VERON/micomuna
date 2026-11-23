package com.micomuna.dtos;

import java.util.List;

import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;

import io.swagger.v3.oas.annotations.media.Schema;

// El descarte tal como lo ve el vecino. Del reclamo original (si es un duplicado) solo se informa su estado y sus cambios
// de estado: no su número, ni su descripción, ni quién lo reportó.
public record DescarteDto(
		MotivoDescarte motivo,
		@Schema(nullable = true, description = "Lo que escribió la comuna al descartarlo") String observacion,
		@Schema(nullable = true, description = "Solo con motivo DUPLICADO: el estado actual del reclamo que sigue la comuna. No se informa su número ni ningún dato de otro vecino") EstadoReclamo estadoOriginal,
		@Schema(description = "Solo con motivo DUPLICADO: los cambios de estado del reclamo que sigue la comuna (estado y fecha, sin reasignaciones). Vacío en los demás motivos") List<CambioDeEstadoDto> historialOriginal) {
}
