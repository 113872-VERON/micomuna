package com.micomuna.exceptions;

// "Dejar como nuevo" no se puede: ya se marcó (YA_MARCADO) o no hay posibles duplicados cerca (SIN_CANDIDATOS). Responde 409.
public class RevisionDeDuplicadosException extends RuntimeException {

	private final String codigo;

	public RevisionDeDuplicadosException(String codigo, String mensaje) {
		super(mensaje);
		this.codigo = codigo;
	}

	public String getCodigo() {
		return codigo;
	}
}
