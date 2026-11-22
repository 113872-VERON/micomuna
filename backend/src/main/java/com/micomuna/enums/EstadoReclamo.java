package com.micomuna.enums;

import java.util.EnumSet;
import java.util.Set;

// Estados de un reclamo, los mismos que admite la base (ck_reclamo_estado en V5).
// Todo reclamo nace RECIBIDO. Cada estado sabe a cuáles puede pasar (MC-17, CambioDeEstadoService):
//   RECIBIDO      -> ASIGNADO, DESCARTADO
//   ASIGNADO      -> EN_REPARACION, RECIBIDO (se desasigna), DESCARTADO
//   EN_REPARACION -> RESUELTO, ASIGNADO
//   RESUELTO      -> EN_REPARACION (se reabre)
//   DESCARTADO    -> RECIBIDO (se reabre)
public enum EstadoReclamo {
	RECIBIDO,
	ASIGNADO,
	EN_REPARACION,
	RESUELTO,
	DESCARTADO;

	public Set<EstadoReclamo> siguientesPermitidos() {
		return switch (this) {
			case RECIBIDO -> EnumSet.of(ASIGNADO, DESCARTADO);
			case ASIGNADO -> EnumSet.of(EN_REPARACION, RECIBIDO, DESCARTADO);
			case EN_REPARACION -> EnumSet.of(RESUELTO, ASIGNADO);
			case RESUELTO -> EnumSet.of(EN_REPARACION);
			case DESCARTADO -> EnumSet.of(RECIBIDO);
		};
	}

	public boolean puedePasarA(EstadoReclamo nuevo) {
		return siguientesPermitidos().contains(nuevo);
	}
}
