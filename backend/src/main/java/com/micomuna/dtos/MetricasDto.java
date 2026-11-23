package com.micomuna.dtos;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record MetricasDto(
		LocalDate desde,
		LocalDate hasta,
		@Schema(description = "Cantidad de días del rango, contando los dos extremos") int dias,
		@Schema(description = "Los creados en el rango, en cualquier estado") long reclamosDelPeriodo,
		@Schema(description = "Los que pasaron a RESUELTO dentro del rango (por la última vez que pasaron)") long resueltos,
		@Schema(nullable = true, description = "Con un decimal. Vacío si no hubo resueltos en el período") Double tiempoPromedioResolucionHoras,
		@Schema(description = "De los creados en el rango, los que hoy siguen en RECIBIDO") long sinAsignar,
		@Schema(description = "Los seis tipos, en el orden de /api/tipos-reclamo, con 0 si no hubo") List<CantidadPorTipoDto> porTipo,
		@Schema(description = "Una fila por semana del rango, en orden, con 0 si no hubo") List<CantidadPorSemanaDto> porSemana) {
}
