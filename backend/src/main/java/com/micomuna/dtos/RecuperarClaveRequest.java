package com.micomuna.dtos;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecuperarClaveRequest(
		@NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") String correo) {

	public RecuperarClaveRequest {
		correo = correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
	}
}
