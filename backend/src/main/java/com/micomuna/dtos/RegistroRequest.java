package com.micomuna.dtos;

import java.util.Locale;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroRequest(
		@NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "El nombre no puede superar los 100 caracteres") String nombre,
		@NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") @Size(max = 254, message = "El correo no puede superar los 254 caracteres") String correo,
		@Schema(description = "De 8 a 72 caracteres", format = "password") @ClaveValida String clave) {

	// El correo se guarda sin espacios y en minúsculas, así "Ana@Mail.com" y "ana@mail.com" son la misma cuenta.
	public RegistroRequest {
		nombre = nombre == null ? null : nombre.trim();
		correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
	}
}
