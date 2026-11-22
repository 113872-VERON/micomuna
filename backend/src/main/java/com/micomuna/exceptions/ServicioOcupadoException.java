package com.micomuna.exceptions;

// El servidor está al límite de un trabajo pesado (por ejemplo, procesar fotos) y no puede atender ahora (503).
public class ServicioOcupadoException extends RuntimeException {

	public ServicioOcupadoException(String mensaje) {
		super(mensaje);
	}
}
