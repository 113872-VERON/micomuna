package com.micomuna.dtos;

import java.util.Locale;

import com.micomuna.enums.Rol;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// Cambio parcial: lo que no se envía no se toca. Tiene que venir al menos un campo (lo revisa el servicio).
@Schema(description = "Cambio parcial. Al menos un campo.")
public record ActualizarUsuarioRequest(
		@Schema(allowableValues = { "VECINO", "ADMINISTRATIVO", "AGENTE", "ADMIN_COMUNA" }) Rol rol,
		Boolean activo,
		@Schema(description = "Solo para usuarios PENDIENTE (sin contraseña).") @Email(message = "El correo no es válido") @Size(min = 1, max = 254, message = "El correo no puede estar vacío ni superar los 254 caracteres") String correo) {

	// Igual que en el registro: el correo va sin espacios y en minúsculas.
	public ActualizarUsuarioRequest {
		correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
	}

	public boolean estaVacio() {
		return rol == null && activo == null && correo == null;
	}
}
