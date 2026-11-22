package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

// Una fila del historial tal como la ve el vecino: solo el estado y la fecha. Nunca quién lo cambió ni la nota interna.
public record CambioDeEstadoDto(EstadoReclamo estado, Instant fecha) {
}
