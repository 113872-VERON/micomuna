package com.micomuna.enums;

// Estados de un reclamo, los mismos que admite la base (ck_reclamo_estado en V5).
// Todo reclamo nace RECIBIDO; los cambios de estado llegan con sus propias historias.
public enum EstadoReclamo {
	RECIBIDO,
	ASIGNADO,
	EN_REPARACION,
	RESUELTO,
	DESCARTADO
}
