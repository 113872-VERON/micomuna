package com.micomuna.enums;

import java.util.Locale;

// Orden de la lista del panel (MC-27): por fecha (el más nuevo primero, el predeterminado) o por prioridad efectiva.
public enum OrdenPanel {
	FECHA,
	PRIORIDAD;

	// Vacío o ausente es FECHA; si no, "fecha" o "prioridad" (en minúsculas, como en el contrato). Otro texto es null.
	public static OrdenPanel interpretar(String texto) {
		if (texto == null || texto.isBlank()) {
			return FECHA;
		}
		return switch (texto.strip().toLowerCase(Locale.ROOT)) {
			case "fecha" -> FECHA;
			case "prioridad" -> PRIORIDAD;
			default -> null;
		};
	}
}
