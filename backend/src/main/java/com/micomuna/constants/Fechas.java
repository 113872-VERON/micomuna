package com.micomuna.constants;

import java.time.ZoneId;

// Las fechas del negocio (los "días" de las métricas) son de calendario argentino.
public final class Fechas {

	public static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

	private Fechas() {
	}
}
