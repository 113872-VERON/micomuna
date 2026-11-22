package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

// Un reclamo en la lista del vecino. Lo arma directamente la consulta (constructor de ReclamoRepository.misReclamos):
// no hay entidades ni fotos de por medio.
public record MiReclamoResumenDto(
		Integer numero,
		@Schema(description = "Código del tipo de problema") String tipo,
		@Schema(description = "Nombre para mostrar, por ejemplo \"Bache o calle rota\"") String tipoNombre,
		EstadoReclamo estado,
		String descripcion,
		@Schema(nullable = true) String direccion,
		Instant creadoEn,
		@Schema(description = "Fecha del último cambio de estado (al crearlo, la misma que creadoEn)") Instant estadoCambiadoEn,
		@Schema(nullable = true, description = "La primera foto que subió el vecino, para la miniatura; vacío si no subió ninguna") Long idFotoPrincipal) {
}
