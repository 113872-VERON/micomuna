package com.micomuna.exceptions;

// Un solo error para todo lo que falle al iniciar sesión, así no se revela la causa.
public class CredencialesInvalidasException extends RuntimeException {

	public CredencialesInvalidasException() {
		super("Credenciales incorrectas");
	}
}
