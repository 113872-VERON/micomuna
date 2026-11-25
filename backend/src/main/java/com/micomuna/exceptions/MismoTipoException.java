package com.micomuna.exceptions;

// Se quiso poner el tipo que el reclamo ya tiene y no hay una sugerencia de la IA pendiente de decidir. Responde 409 MISMO_TIPO.
public class MismoTipoException extends RuntimeException {

	public MismoTipoException(String mensaje) {
		super(mensaje);
	}
}
