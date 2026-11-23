package com.micomuna.dtos;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

public record CantidadPorSemanaDto(
		@Schema(description = "El lunes de la semana, o el primer día del rango si empieza a mitad de semana") LocalDate desde,
		@Schema(description = "El domingo de la semana, o el último día del rango si termina a mitad de semana") LocalDate hasta,
		long cantidad) {
}
