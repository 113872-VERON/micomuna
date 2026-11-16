package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Sirve para restablecer la clave y para aceptar una invitación: en los dos casos llega el código del correo y la clave elegida.
public record RestablecerClaveRequest(
		@Schema(description = "Código que vino en el enlace del correo") @NotBlank(message = "El código es obligatorio") @Size(max = 100, message = "El código no es válido") String codigo,
		@Schema(description = "De 8 a 72 caracteres", format = "password") @ClaveValida String claveNueva) {
}
