package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarTipoRequest(
		@Schema(description = "Código del tipo (ver /api/tipos-reclamo)")
		@NotBlank(message = "Elegí el tipo") @Size(max = 20, message = "El tipo no es válido") String tipo) {
}
