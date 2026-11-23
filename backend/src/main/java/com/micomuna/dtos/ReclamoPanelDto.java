package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Una fila de la lista del panel.
public record ReclamoPanelDto(
		Integer numero,
		String tipo,
		String tipoNombre,
		EstadoReclamo estado,
		String descripcion,
		@Schema(nullable = true) String direccion,
		@Schema(nullable = true) String referencia,
		Instant creadoEn,
		@Schema(nullable = true) AgenteDto agente,
		@Schema(nullable = true, description = "Si se descartó por duplicado, el número del original") Integer numeroOriginal,
		@Schema(description = "Cuántos reclamos están descartados como duplicados de este") int cantidadDuplicados) {
}
