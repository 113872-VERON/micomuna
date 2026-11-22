package com.micomuna.services;

import java.util.function.LongSupplier;

import com.micomuna.exceptions.LimiteSuperadoException;

// Deja pasar como mucho un pedido por segundo, en total. Nunca hace esperar a nadie: si todavia no paso un segundo
// desde el ultimo pedido que salio, rechaza enseguida (429 DEMASIADOS_PEDIDOS). Simple y en memoria.
class LimitadorPorSegundo {

	static final long INTERVALO_NANOS = 1_000_000_000L;
	static final String CODIGO = "DEMASIADOS_PEDIDOS";
	static final String MENSAJE = "Se están pidiendo muchas direcciones a la vez. Probá de nuevo en unos segundos.";

	private final LongSupplier relojNanos;
	private boolean huboPedido;
	private long ultimoPedido;

	LimitadorPorSegundo(LongSupplier relojNanos) {
		this.relojNanos = relojNanos;
	}

	// Lanza LimiteSuperadoException si no hay turno en este momento; si hay, lo toma.
	synchronized void tomarTurno() {
		long ahora = relojNanos.getAsLong();
		if (huboPedido && ahora - ultimoPedido < INTERVALO_NANOS) {
			throw new LimiteSuperadoException(CODIGO, MENSAJE);
		}
		huboPedido = true;
		ultimoPedido = ahora;
	}
}
