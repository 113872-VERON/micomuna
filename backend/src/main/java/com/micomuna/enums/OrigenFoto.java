package com.micomuna.enums;

// Quién sacó la foto de un reclamo (ck_reclamo_foto_origen en V9). El orden de las constantes es el orden
// en que se muestran: primero las del vecino y después las del trabajo.
public enum OrigenFoto {
	// Las que subió el vecino al reportar.
	VECINO,
	// Las que sacó el agente al resolverlo.
	TRABAJO
}
