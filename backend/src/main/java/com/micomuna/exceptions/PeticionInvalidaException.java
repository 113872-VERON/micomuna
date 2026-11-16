package com.micomuna.exceptions;

// Un pedido bien formado que no se puede cumplir por una regla de negocio (400), con un código para el cliente.
public class PeticionInvalidaException extends RuntimeException {

	private final String codigo;

	public PeticionInvalidaException(String codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}
}
