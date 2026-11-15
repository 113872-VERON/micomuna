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
		String motivo = motivoDeRechazo(clave);
		if (motivo == null) {
			return true;
		}
		contexto.disableDefaultConstraintViolation();
		contexto.buildConstraintViolationWithTemplate(motivo).addConstraintViolation();
		return false;
	}

	// Devuelve por qué la clave no sirve, o null si es válida. Se usa también fuera de los formularios.
	public static String motivoDeRechazo(String clave) {
		if (clave == null) {
			return "La contraseña es obligatoria";
		}
		if (clave.length() < MINIMO_CARACTERES) {
			return "La contraseña debe tener al menos " + MINIMO_CARACTERES + " caracteres";
		}
		if (clave.getBytes(StandardCharsets.UTF_8).length > MAXIMO_BYTES) {
			return "La contraseña no puede superar los " + MAXIMO_BYTES + " caracteres";
		}
		return null;
	}
}
