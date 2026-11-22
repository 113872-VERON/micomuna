package com.micomuna.exceptions;

import com.micomuna.enums.EstadoReclamo;

// Se pidió un cambio de estado que las reglas no permiten (por ejemplo, RESUELTO a DESCARTADO).
// Responde 409 con el código TRANSICION_INVALIDA: el pedido está bien armado, pero choca con el estado actual.
public class TransicionInvalidaException extends RuntimeException {

	private final EstadoReclamo desde;
	private final EstadoReclamo hacia;

	public TransicionInvalidaException(EstadoReclamo desde, EstadoReclamo hacia) {
		super("Un reclamo " + desde + " no puede pasar a " + hacia);
		this.desde = desde;
		this.hacia = hacia;
	}

	public EstadoReclamo getDesde() {
		return desde;
	}

	public EstadoReclamo getHacia() {
		return hacia;
	}
}
