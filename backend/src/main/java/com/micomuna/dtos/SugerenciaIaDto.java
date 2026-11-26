package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.DecisionSugerenciaIa;
import com.micomuna.enums.EstadoSugerenciaPanel;
import com.micomuna.enums.PrioridadIa;

import io.swagger.v3.oas.annotations.media.Schema;

// La sugerencia de tipo de la IA vigente (la más nueva) para el personal. Nunca lleva el detalle del error, el modelo ni los
// tokens. La prioridad sugerida viaja solo con HECHA (MC-27).
public record SugerenciaIaDto(
		EstadoSugerenciaPanel estado,
		@Schema(nullable = true, description = "Código del tipo sugerido. Solo con HECHA") String tipoSugerido,
		@Schema(nullable = true) String tipoSugeridoNombre,
		@Schema(nullable = true, description = "Qué vio o leyó la IA para sugerirlo (hasta 300 caracteres). Solo con HECHA") String justificacion,
		@Schema(description = "true si la sugerencia es HECHA y el tipo sugerido es el tipo actual del reclamo") boolean coincide,
		@Schema(nullable = true, description = "Qué decidió el personal sobre la sugerencia. Vacío si todavía no decidió") DecisionSugerenciaIa decision,
		@Schema(nullable = true) String tipoDecidido,
		@Schema(nullable = true) String tipoDecididoNombre,
		@Schema(nullable = true, description = "Nombre de quién decidió") String decididaPor,
		@Schema(nullable = true) Instant decididaEn,
		@Schema(nullable = true, description = "Prioridad que sugirió la IA. Solo con HECHA") PrioridadIa prioridadSugerida) {

	public static SugerenciaIaDto sinAnalizar() {
		return new SugerenciaIaDto(EstadoSugerenciaPanel.SIN_ANALIZAR, null, null, null, false, null, null, null, null, null, null);
	}

	public static SugerenciaIaDto noDisponible() {
		return new SugerenciaIaDto(EstadoSugerenciaPanel.NO_DISPONIBLE, null, null, null, false, null, null, null, null, null, null);
	}
}
