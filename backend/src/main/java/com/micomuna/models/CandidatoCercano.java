package com.micomuna.models;

import java.math.BigDecimal;
import java.time.Instant;

import com.micomuna.enums.EstadoReclamo;

// Fila de la búsqueda de posibles duplicados (MC-26): lo justo para calcular la distancia y armar el aviso.
public record CandidatoCercano(Long id, Integer numero, EstadoReclamo estado, Instant creadoEn, String direccion,
		String descripcion, BigDecimal latitud, BigDecimal longitud) {
}
