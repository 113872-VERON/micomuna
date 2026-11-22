package com.micomuna.services;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

import com.micomuna.exceptions.LimiteSuperadoException;

// Tope de pedidos por usuario en una ventana de un minuto (ventana deslizante, en memoria). Cada usuario guarda
// solo la hora de sus ultimos pedidos. Para no crecer sin fin, cuando hay demasiados usuarios anotados se descartan
// los que no pidieron en el ultimo minuto; si aun asi no hay lugar, el usuario nuevo se rechaza (mejor eso que
// dejar pasar sin control).
class LimitadorPorUsuario {

	static final int PEDIDOS_POR_MINUTO = 10;
	static final long VENTANA_NANOS = 60L * 1_000_000_000L;
	static final int USUARIOS_MAXIMOS = 10_000;

	private final LongSupplier relojNanos;
	private final Map<Long, ArrayDeque<Long>> pedidos = new HashMap<>();

	LimitadorPorUsuario(LongSupplier relojNanos) {
		this.relojNanos = relojNanos;
	}

	// Anota un pedido del usuario. Lanza LimiteSuperadoException si ya hizo 10 en el ultimo minuto.
	synchronized void registrar(Long usuarioId) {
		long ahora = relojNanos.getAsLong();
		ArrayDeque<Long> horas = pedidos.get(usuarioId);
		if (horas == null) {
			if (pedidos.size() >= USUARIOS_MAXIMOS) {
				descartarInactivos(ahora);
				if (pedidos.size() >= USUARIOS_MAXIMOS) {
					throw rechazo();
				}
			}
			horas = new ArrayDeque<>();
			pedidos.put(usuarioId, horas);
		}
		while (!horas.isEmpty() && ahora - horas.peekFirst() >= VENTANA_NANOS) {
			horas.pollFirst();
		}
		if (horas.size() >= PEDIDOS_POR_MINUTO) {
			throw rechazo();
		}
		horas.addLast(ahora);
	}

	private void descartarInactivos(long ahora) {
		pedidos.values().removeIf(horas -> horas.isEmpty() || ahora - horas.peekLast() >= VENTANA_NANOS);
	}

	synchronized int usuariosAnotados() {
		return pedidos.size();
	}

	private static LimiteSuperadoException rechazo() {
		return new LimiteSuperadoException(LimitadorPorSegundo.CODIGO, LimitadorPorSegundo.MENSAJE);
	}
}
