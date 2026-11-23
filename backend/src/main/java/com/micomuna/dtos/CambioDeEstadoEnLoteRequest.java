package com.micomuna.dtos;

import java.util.List;

import com.micomuna.enums.EstadoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioDeEstadoEnLoteRequest(
		@NotEmpty(message = "Elegí al menos un reclamo") @Size(max = 100, message = "Podés cambiar hasta 100 reclamos a la vez") @Schema(description = "Sin repetidos") List<@NotNull(message = "Número inválido") @Min(value = 1, message = "Número inválido") Integer> numeros,
		@NotNull(message = "Elegí el estado") @Schema(allowableValues = { "RECIBIDO", "ASIGNADO", "EN_REPARACION", "RESUELTO" }) EstadoReclamo estado,
		@Schema(nullable = true, description = "Obligatorio con ASIGNADO; no se acepta con otro estado") Long idAgente) {
}
