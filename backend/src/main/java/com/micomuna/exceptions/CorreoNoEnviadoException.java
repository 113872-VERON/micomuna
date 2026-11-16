package com.micomuna.exceptions;

// El servidor de correo no aceptó el mensaje. Se avisa para que la persona reintente.
public class CorreoNoEnviadoException extends RuntimeException {

	public CorreoNoEnviadoException(String mensaje) {
		super(mensaje);
	}
}
