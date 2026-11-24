package com.micomuna.enums;

// Cómo terminó un análisis de la IA (ck_sugerencia_ia_estado en V12).
public enum EstadoSugerenciaIa {
	// La IA respondió y la respuesta es válida.
	HECHA,
	// Se llamó a la IA y falló (o respondió algo inválido).
	ERROR,
	// No se llamó: la IA está apagada, falta la clave, se pasó el tope diario o la cola de análisis estaba llena.
	OMITIDA
}
