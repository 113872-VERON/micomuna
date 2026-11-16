package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record CambiarClaveRequest(
		@Schema(format = "password") @NotBlank(message = "La contraseña actual es obligatoria") String claveActual,
		@Schema(description = "De 8 a 72 caracteres, distinta de la actual", format = "password") @ClaveValida String claveNueva) {
}
