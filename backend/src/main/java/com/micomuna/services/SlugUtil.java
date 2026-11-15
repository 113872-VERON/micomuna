package com.micomuna.services;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import com.micomuna.enums.Provincia;

// El enlace de una comuna (por ejemplo "salsipuedes-cordoba"): minúsculas, números y guiones simples en el medio.
public final class SlugUtil {

	public static final int MAXIMO = 120;
	public static final String EXPRESION = "[a-z0-9]+(-[a-z0-9]+)*";

	private static final Pattern VALIDO = Pattern.compile(EXPRESION);

	private SlugUtil() {
	}

	// Lanza IllegalArgumentException si el nombre no tiene ni una letra ni un número.
	public static String generar(String nombre, Provincia provincia) {
		String parteNombre = simplificar(nombre);
		if (parteNombre.isEmpty()) {
			throw new IllegalArgumentException("El nombre no tiene letras ni números");
		}
		return parteNombre + "-" + simplificar(provincia.name());
	}

	public static boolean esValido(String slug) {
		return slug != null && slug.length() <= MAXIMO && VALIDO.matcher(slug).matches();
	}

	// Sin tildes ni símbolos: todo lo que no sea letra o número se vuelve un solo guion.
	private static String simplificar(String texto) {
		String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}
}
