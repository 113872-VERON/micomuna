package com.micomuna.dtos;

import com.micomuna.enums.OrigenFoto;

import io.swagger.v3.oas.annotations.media.Schema;

// Los datos de una foto, nunca la imagen: la imagen se pide aparte (la consulta ni siquiera la lee de la base).
public record FotoReclamoDto(
		@Schema(description = "Para pedir la imagen a /api/reclamos/mios/{numero}/fotos/{id}") Long id,
		OrigenFoto origen,
		@Schema(minimum = "1", maximum = "3") int orden) {
}
