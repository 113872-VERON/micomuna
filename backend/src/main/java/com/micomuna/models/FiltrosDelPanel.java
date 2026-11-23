package com.micomuna.models;

import java.util.Locale;

import com.micomuna.enums.EstadoReclamo;

// Los filtros del panel de reclamos ya interpretados (ver PanelReclamosService). Todos son opcionales.
//  - numero: si lo escrito en "buscar" eran solo dígitos, ese número de reclamo;
//  - texto: si no, el texto a buscar en dirección y referencia, ya en minúsculas y sin tildes en las vocales.
public record FiltrosDelPanel(EstadoReclamo estado, String tipo, Long agente, Integer numero, String texto,
		boolean numeroImposible) {

	public static final String CON_TILDES = "áéíóúüñ";
	public static final String SIN_TILDES = "aeiouun";

	public FiltrosDelPanel sinEstado() {
		return new FiltrosDelPanel(null, tipo, agente, numero, texto, numeroImposible);
	}

	// Minúsculas, vocales sin tilde y la ñ como n (así "penaloza" encuentra "Peñaloza"). Es lo mismo que hace la base con la columna.
	public static String normalizar(String texto) {
		String minuscula = texto.toLowerCase(Locale.ROOT);
		StringBuilder resultado = new StringBuilder(minuscula.length());
		for (char c : minuscula.toCharArray()) {
			int i = CON_TILDES.indexOf(c);
			resultado.append(i >= 0 ? SIN_TILDES.charAt(i) : c);
		}
		return resultado.toString();
	}
}
