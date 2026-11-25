package com.micomuna.dtos;

import java.time.Instant;
import java.util.List;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReclamoPanelDetalleDto(
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
		@Schema(description = "Nombre del vecino que lo reportó (no se muestran su correo ni otros datos)") String vecinoNombre,
		@Schema(nullable = true) AgenteDto agente,
		@Schema(description = "Primero las del vecino y después las del trabajo (solo si está RESUELTO), cada grupo en su orden") List<FotoReclamoDto> fotos,
		@Schema(description = "Del más viejo al más nuevo") List<CambioDeEstadoPanelDto> historial,
		@Schema(nullable = true, description = "Solo si el estado es DESCARTADO") DescartePanelDto descarte,
		@Schema(nullable = true, description = "Solo si el estado es RESUELTO") String trabajoRealizado,
		@Schema(description = "Números de los reclamos descartados como duplicados de este") List<Integer> duplicados,
		@Schema(description = "La sugerencia de tipo de la IA vigente. Siempre presente") SugerenciaIaDto sugerenciaIa) {
}
