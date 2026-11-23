package com.micomuna.dtos;

import java.time.Instant;
import java.util.List;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Lo que el agente necesita para ir y resolver. A propósito sin nombre, correo ni teléfono del vecino.
public record ReclamoAgenteDetalleDto(
		Integer numero,
		String tipo,
		String tipoNombre,
		EstadoReclamo estado,
		String descripcion,
		@Schema(nullable = true) String referencia,
		@Schema(nullable = true) String direccion,
		@Schema(nullable = true) Double latitud,
		@Schema(nullable = true) Double longitud,
		Instant creadoEn,
		@Schema(description = "Primero las del vecino y después las del trabajo (solo si está RESUELTO)") List<FotoReclamoDto> fotos,
		@Schema(nullable = true, description = "Solo si está RESUELTO") String trabajoRealizado) {
}
