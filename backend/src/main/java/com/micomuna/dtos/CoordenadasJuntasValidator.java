package com.micomuna.dtos;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CoordenadasJuntasValidator implements ConstraintValidator<CoordenadasJuntas, CrearReclamoForm> {

	// Las coordenadas las manda el celular, no las escribe el vecino: si llegan mal, se le dice qué hacer.
	static final String UBICACION_NO_LEIDA = "No pudimos leer tu ubicación. Marcá en el mapa dónde está el problema o tocá «Usar mi ubicación» de nuevo";

	@Override
	public boolean isValid(CrearReclamoForm form, ConstraintValidatorContext contexto) {
		if (form == null || (form.latitud() == null) == (form.longitud() == null)) {
			return true;
		}
		String falta = form.latitud() == null ? "latitud" : "longitud";
		contexto.disableDefaultConstraintViolation();
		contexto.buildConstraintViolationWithTemplate(UBICACION_NO_LEIDA)
				.addPropertyNode(falta)
				.addConstraintViolation();
		return false;
	}
}
