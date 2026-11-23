package com.micomuna.models;

import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;

// Lo que se le pide a CambioDeEstadoService. Solo el estado nuevo es obligatorio; el resto depende del cambio:
//  - a ASIGNADO: idAgente (obligatorio: un usuario activo con rol AGENTE de la comuna). Con otro estado no se acepta;
//  - a DESCARTADO: motivo (obligatorio), observacion (obligatoria salvo con motivo DUPLICADO, hasta 300 caracteres) y,
//    si el motivo es DUPLICADO, numeroOriginal (obligatorio: el número del reclamo que sigue la comuna, de la misma
//    comuna y no descartado);
//  - a RESUELTO: trabajoRealizado (opcional en este servicio, hasta 1000 caracteres; lo exige quien lo llame);
//  - nota: texto interno del cambio (se guarda en el historial, nunca se le muestra al vecino).
public record CambioDeEstado(
		EstadoReclamo nuevo,
		MotivoDescarte motivo,
		String observacion,
		Integer numeroOriginal,
		String trabajoRealizado,
		String nota,
		Long idAgente) {

	public CambioDeEstado(EstadoReclamo nuevo, MotivoDescarte motivo, String observacion, Integer numeroOriginal,
			String trabajoRealizado, String nota) {
		this(nuevo, motivo, observacion, numeroOriginal, trabajoRealizado, nota, null);
	}

	public static CambioDeEstado a(EstadoReclamo nuevo) {
		return new CambioDeEstado(nuevo, null, null, null, null, null, null);
	}

	public static CambioDeEstado asignar(Long idAgente) {
		return new CambioDeEstado(EstadoReclamo.ASIGNADO, null, null, null, null, null, idAgente);
	}

	public static CambioDeEstado descartar(MotivoDescarte motivo, String observacion, Integer numeroOriginal) {
		return new CambioDeEstado(EstadoReclamo.DESCARTADO, motivo, observacion, numeroOriginal, null, null, null);
	}

	public static CambioDeEstado resolver(String trabajoRealizado) {
		return new CambioDeEstado(EstadoReclamo.RESUELTO, null, null, null, trabajoRealizado, null, null);
	}
}
