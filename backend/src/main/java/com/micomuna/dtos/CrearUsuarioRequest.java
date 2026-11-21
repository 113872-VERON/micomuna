package com.micomuna.dtos;

import java.util.Locale;

import com.micomuna.enums.Rol;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Alta de una persona del personal. Que el rol sea de personal (no VECINO ni SUPERADMIN) lo revisa el servicio.
public record CrearUsuarioRequest(
		@NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "El nombre no puede superar los 100 caracteres") String nombre,
		@NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") @Size(max = 254, message = "El correo no puede superar los 254 caracteres") String correo,
		@Schema(description = "Solo personal. Los vecinos se registran solos.", allowableValues = { "ADMINISTRATIVO", "AGENTE", "ADMIN_COMUNA" }) @NotNull(message = "El rol es obligatorio") Rol rol) {

	// Igual que en el registro: el correo va sin espacios y en minúsculas.
	public CrearUsuarioRequest {
		nombre = nombre == null ? null : nombre.trim();
		correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
	}
}
