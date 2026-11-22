package com.micomuna.services;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.micomuna.exceptions.ServicioOcupadoException;

// Limita cuántos pedidos procesan fotos a la vez (abrirlas y volver a armarlas es lo que más memoria usa).
// Con 3 turnos y el tope de memoria por foto de ImagenUtil (40 MiB), en el peor caso hay unos 120 MiB de píxeles
// abiertos al mismo tiempo. Quien no consigue turno espera un poco; si sigue lleno, responde 503 OCUPADO
// en lugar de seguir sumando memoria hasta que el servidor se caiga.
@Component
public class TurnosDeFotos {

	static final int TURNOS = 3;
	static final long ESPERA_MAXIMA_SEGUNDOS = 5;
	static final String OCUPADO = "Estamos procesando muchas fotos en este momento. Probá de nuevo en unos segundos.";

	// Justo (fair): los pedidos que esperan se atienden en el orden en que llegaron.
	private final Semaphore turnos = new Semaphore(TURNOS, true);

	public <T> T conTurno(Supplier<T> trabajo) {
		boolean conseguido;
		try {
			conseguido = turnos.tryAcquire(ESPERA_MAXIMA_SEGUNDOS, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new ServicioOcupadoException(OCUPADO);
		}
		if (!conseguido) {
			throw new ServicioOcupadoException(OCUPADO);
		}
		try {
			return trabajo.get();
		} finally {
			turnos.release();
		}
	}
}
