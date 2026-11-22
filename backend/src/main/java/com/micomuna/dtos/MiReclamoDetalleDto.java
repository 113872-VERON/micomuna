package com.micomuna.dtos;

import java.time.Instant;
import java.util.List;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

public record MiReclamoDetalleDto(
		Integer numero,
		String tipo,
		String tipoNombre,
		EstadoReclamo estado,
		String descripcion,
		@Schema(nullable = true) String referencia,
		@Schema(nullable = true) String direccion,
		Double latitud,
		Double longitud,
		Instant creadoEn,
		@Schema(description = "Primero las del vecino y después las del trabajo, cada grupo en su orden") List<FotoReclamoDto> fotos,
		@Schema(description = "Los cambios de estado, del más viejo al más nuevo. El primero es siempre RECIBIDO, con la fecha de creación") List<CambioDeEstadoDto> historial,
		@Schema(nullable = true, description = "Solo si el estado es DESCARTADO") DescarteDto descarte,
		@Schema(nullable = true, description = "Lo que escribió el agente al resolverlo. Solo si el estado es RESUELTO") String trabajoRealizado) {
}
