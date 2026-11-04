package com.micomuna.dtos;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ClaveValidaValidator implements ConstraintValidator<ClaveValida, String> {

	static final int MINIMO_CARACTERES = 8;
	// BCrypt solo usa los primeros 72 bytes: una clave más larga se truncaría en silencio.
	static final int MAXIMO_BYTES = 72;

	@Override
	public boolean isValid(String clave, ConstraintValidatorContext contexto) {
		if (clave == null) {
			return rechazar(contexto, "La contraseña es obligatoria");
		}
		if (clave.length() < MINIMO_CARACTERES) {
			return rechazar(contexto, "La contraseña debe tener al menos " + MINIMO_CARACTERES + " caracteres");
		}
		if (clave.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
			return rechazar(contexto, "La contraseña no puede superar los " + MAXIMO_BYTES + " caracteres");
		}
		return true;
	}

	private boolean rechazar(ConstraintValidatorContext contexto, String mensaje) {
		contexto.disableDefaultConstraintViolation();
		contexto.buildConstraintViolationWithTemplate(mensaje).addConstraintViolation();
		return false;
	}
}
