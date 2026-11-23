package com.micomuna.dtos;

import java.util.List;

import com.micomuna.enums.MotivoDescarte;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// El motivo no lleva @NotNull a propósito: su falta responde MOTIVO_REQUERIDO (código propio del contrato).
public record DescarteEnLoteRequest(
		@NotEmpty(message = "Elegí al menos un reclamo") @Size(max = 100, message = "Podés descartar hasta 100 reclamos a la vez") @Schema(description = "Sin repetidos") List<@NotNull(message = "Número inválido") @Min(value = 1, message = "Número inválido") Integer> numeros,
		MotivoDescarte motivo,
		@Size(max = 300, message = "La observación puede tener hasta 300 caracteres") @Schema(nullable = true, description = "Obligatoria salvo con DUPLICADO. El vecino la lee: no poner datos de otras personas") String observacion,
		@Schema(nullable = true, description = "Obligatorio con DUPLICADO; no se acepta con otro motivo") Integer numeroOriginal) {
}
