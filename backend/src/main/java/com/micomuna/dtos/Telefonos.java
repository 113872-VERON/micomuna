package com.micomuna.dtos;

// Reglas del teléfono y del WhatsApp de una comuna: solo dígitos, con código de país, de 8 a 15 (igual que el CHECK de la base).
final class Telefonos {

	static final String EXPRESION = "^54[0-9]{10,11}$";
	static final String EXPRESION_O_VACIO = "^(54[0-9]{10,11})?$";
	static final String MENSAJE = "Escribí el número con el código de país 54, sin el 0 ni el 15 (por ejemplo 5493543123456).";

	private Telefonos() {
	}

	// Quita espacios, guiones, paréntesis y un "+" inicial. Lo demás (letras, puntos) se deja para que la validación lo rechace.
	static String normalizar(String texto) {
		if (texto == null) {
			return null;
		}
		String limpio = texto.replaceAll("[\\s\\-()]", "");
		return limpio.startsWith("+") ? limpio.substring(1) : limpio;
	}
}
