package com.micomuna.exceptions;

// Se quiso asignar un reclamo al agente que ya lo tiene. Responde 409 YA_ASIGNADO.
public class YaAsignadoException extends RuntimeException {

	public YaAsignadoException(String mensaje) {
		super(mensaje);
	}
}
