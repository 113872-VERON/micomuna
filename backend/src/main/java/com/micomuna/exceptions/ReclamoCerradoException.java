package com.micomuna.exceptions;

// Se quiso asignar o reasignar un reclamo que ya está RESUELTO o DESCARTADO. Responde 409 RECLAMO_CERRADO.
public class ReclamoCerradoException extends RuntimeException {

	public ReclamoCerradoException(String mensaje) {
		super(mensaje);
	}
}
