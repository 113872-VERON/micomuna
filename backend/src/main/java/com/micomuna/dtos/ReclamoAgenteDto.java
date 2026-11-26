package com.micomuna.dtos;

import java.math.BigDecimal;
import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Una tarjeta de la lista del agente (MC-20). La arma directamente la consulta de ReclamoRepository: sin entidades,
// sin fotos y sin datos del vecino.
public record ReclamoAgenteDto(
		Integer numero,
		@Schema(description = "Código del tipo de problema") String tipo,
		String tipoNombre,
		EstadoReclamo estado,
		@Schema(nullable = true) String direccion,
		@Schema(nullable = true) String referencia,
		@Schema(nullable = true) Double latitud,
		@Schema(nullable = true) Double longitud,
		Instant creadoEn,
		@Schema(description = "Fecha del último cambio de estado (en RESUELTOS, cuándo se resolvió)") Instant estadoCambiadoEn,
		@Schema(description = "Prioridad efectiva (MC-27): la del personal o, si no, la sugerida por la IA. Siempre presente") PrioridadReclamoDto prioridad) {

	// Para la consulta: las coordenadas salen de la base como BigDecimal y la prioridad llega como fijada por el personal y sugerida por la IA.
	public ReclamoAgenteDto(Integer numero, String tipo, String tipoNombre, EstadoReclamo estado, String direccion,
			String referencia, BigDecimal latitud, BigDecimal longitud, Instant creadoEn, Instant estadoCambiadoEn,
			com.micomuna.enums.PrioridadIa prioridadFijada, com.micomuna.enums.PrioridadIa prioridadSugerida) {
		this(numero, tipo, tipoNombre, estado, direccion, referencia, latitud == null ? null : latitud.doubleValue(),
				longitud == null ? null : longitud.doubleValue(), creadoEn, estadoCambiadoEn,
				PrioridadReclamoDto.de(prioridadFijada, prioridadSugerida));
	}
}
