package com.micomuna.dtos;

import com.micomuna.enums.OrigenPrioridad;
import com.micomuna.enums.PrioridadIa;

import io.swagger.v3.oas.annotations.media.Schema;

// Prioridad efectiva del reclamo (MC-27): la que fijó el personal; si no, la sugerida por la IA (sugerencia vigente HECHA);
// si no, ninguna. Siempre presente. Nunca se muestra al vecino.
public record PrioridadReclamoDto(
		@Schema(nullable = true, description = "ALTA, MEDIA o BAJA. Vacío con origen NINGUNA") PrioridadIa valor,
		OrigenPrioridad origen) {

	public static PrioridadReclamoDto de(PrioridadIa fijadaPorElPersonal, PrioridadIa sugeridaPorLaIa) {
		if (fijadaPorElPersonal != null) {
			return new PrioridadReclamoDto(fijadaPorElPersonal, OrigenPrioridad.PERSONAL);
		}
		if (sugeridaPorLaIa != null) {
			return new PrioridadReclamoDto(sugeridaPorLaIa, OrigenPrioridad.IA);
		}
		return new PrioridadReclamoDto(null, OrigenPrioridad.NINGUNA);
	}
}
