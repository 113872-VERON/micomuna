package com.micomuna.exceptions;

// Se quiso fijar la misma prioridad que el personal ya había fijado: no hay nada que cambiar. Responde 409 MISMA_PRIORIDAD.
public class MismaPrioridadException extends RuntimeException {

	public MismaPrioridadException(String mensaje) {
		super(mensaje);
	}
}
