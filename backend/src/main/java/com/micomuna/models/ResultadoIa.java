package com.micomuna.models;

import com.micomuna.enums.PrioridadIa;

// Lo que devuelve un AsistenteIa (MC-24): una sugerencia, un error tipificado o "no se analizó".
// Los tokens son opcionales (el modelo local o una omisión pueden no informarlos).
public sealed interface ResultadoIa {

	// La IA respondió. Se la guarda solo si pasa la validación del servidor: tipo existente, prioridad válida y
	// justificación de hasta MAXIMO_JUSTIFICACION caracteres.
	record Hecha(String tipo, PrioridadIa prioridad, String justificacion, Long tokensEntrada, Long tokensSalida)
			implements ResultadoIa {

		public static final int MAXIMO_JUSTIFICACION = 300;

		// La justificación es texto que escribió un modelo: antes de guardarla se le sacan los caracteres de control y los de
		// formato invisibles (saltos de línea, tabulaciones, marcas de dirección del texto, etc.; los espacios normales se
		// dejan) y se recortan los espacios de los extremos. Si queda vacía, no sirve (es un ERROR).
		public static String limpiar(String texto) {
			if (texto == null) {
				return "";
			}
			StringBuilder limpio = new StringBuilder(texto.length());
			texto.codePoints().filter(c -> Character.getType(c) != Character.CONTROL && Character.getType(c) != Character.FORMAT)
					.forEach(limpio::appendCodePoint);
			return limpio.toString().strip();
		}
	}

	// Falló la llamada o la respuesta no sirvió. El detalle es corto y NUNCA lleva datos personales ni texto del vecino.
	record Fallida(String detalle, Long tokensEntrada, Long tokensSalida) implements ResultadoIa {
	}

	// No se analizó (proveedor apagado o sin clave).
	record Omitida() implements ResultadoIa {
	}
}
