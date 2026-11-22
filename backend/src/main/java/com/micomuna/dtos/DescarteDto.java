package com.micomuna.dtos;

import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;

import io.swagger.v3.oas.annotations.media.Schema;

public record DescarteDto(
		MotivoDescarte motivo,
		@Schema(nullable = true, description = "Lo que escribió la comuna al descartarlo") String observacion,
		@Schema(nullable = true, description = "Solo con motivo DUPLICADO: el número del reclamo que sigue la comuna") Integer numeroOriginal,
		@Schema(nullable = true, description = "Solo con motivo DUPLICADO: el estado actual del reclamo original. No trae ningún otro dato de ese reclamo ni de quién lo hizo") EstadoReclamo estadoOriginal) {
}
