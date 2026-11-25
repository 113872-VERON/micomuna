package com.micomuna.enums;

// Cómo le llega la sugerencia de la IA al personal (SugerenciaIaDto). Resume los estados de la base: ERROR y OMITIDA se muestran
// igual (NO_DISPONIBLE) para no exponer por qué falló.
public enum EstadoSugerenciaPanel {
	// Hay una sugerencia.
	HECHA,
	// La IA falló o no se llamó (tope diario o IA apagada).
	NO_DISPONIBLE,
	// Todavía no hay ningún análisis (puede estar en curso, unos segundos después del alta).
	SIN_ANALIZAR
}
