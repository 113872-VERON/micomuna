package com.micomuna.dtos;

import java.util.Locale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") @Size(max = 254, message = "El correo no puede superar los 254 caracteres") String correo,
		@Schema(format = "password") @NotBlank(message = "La contraseña es obligatoria") @Size(max = 200, message = "La contraseña es demasiado larga") String clave) {

	// Mismo criterio que en el registro: sin espacios y en minúsculas. La clave no se toca.
	public LoginRequest {
		correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
	}
}
