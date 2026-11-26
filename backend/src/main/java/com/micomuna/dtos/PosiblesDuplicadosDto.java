package com.micomuna.dtos;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// Aviso de posibles duplicados cercanos (MC-26). Siempre presente en el detalle del panel. Es una regla, sin IA.
public record PosiblesDuplicadosDto(
		@Schema(description = "Hasta 5, del más cercano al más lejano. Vacío si el reclamo está cerrado, no tiene ubicación, ya se marcó como nuevo o no hay ninguno cerca") List<PosibleDuplicadoDto> candidatos,
		@Schema(nullable = true, description = "Nombre de quién lo marcó como reclamo nuevo. Vacío si nadie lo marcó") String marcadoNuevoPor,
		@Schema(nullable = true) Instant marcadoNuevoEn) {
}
