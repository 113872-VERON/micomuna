package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.entities.Reclamo;
import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Lo que ve el vecino al terminar de reportar: sobre todo el número de seguimiento.
public record ReclamoCreadoDto(
		Long id,
		@Schema(description = "Número de seguimiento, correlativo dentro de la comuna") Integer numero,
		String tipo,
		@Schema(allowableValues = { "RECIBIDO" }) EstadoReclamo estado,
		Integer cantidadFotos,
		Instant creadoEn) {

	public static ReclamoCreadoDto desde(Reclamo r, int cantidadFotos) {
		return new ReclamoCreadoDto(r.getId(), r.getNumero(), r.getTipo().getCodigo(), r.getEstado(), cantidadFotos,
				r.getCreadoEn());
	}
}
