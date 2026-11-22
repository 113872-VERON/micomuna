package com.micomuna.exceptions;

// Alguien hizo demasiadas veces lo mismo en poco tiempo (429), con un código para el cliente.
public class LimiteSuperadoException extends RuntimeException {

	private final String codigo;

	public LimiteSuperadoException(String codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}
}
