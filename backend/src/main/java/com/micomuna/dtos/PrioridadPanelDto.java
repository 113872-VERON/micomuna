package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.OrigenPrioridad;
import com.micomuna.enums.PrioridadIa;

import io.swagger.v3.oas.annotations.media.Schema;

// La prioridad efectiva más quién la fijó y cuándo (solo con origen PERSONAL). Solo la ve el personal.
public record PrioridadPanelDto(
		@Schema(nullable = true, description = "ALTA, MEDIA o BAJA. Vacío con origen NINGUNA") PrioridadIa valor,
		OrigenPrioridad origen,
		@Schema(nullable = true, description = "Nombre de quién la fijó") String fijadaPor,
		@Schema(nullable = true) Instant fijadaEn) {

	public static PrioridadPanelDto de(PrioridadReclamoDto efectiva, String fijadaPor, Instant fijadaEn) {
		return new PrioridadPanelDto(efectiva.valor(), efectiva.origen(), fijadaPor, fijadaEn);
	}
}
