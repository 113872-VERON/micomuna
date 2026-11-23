package com.micomuna.dtos;

import com.micomuna.enums.EstadoReclamo;

// Lo justo para dibujar un pin en el mapa.
public record PuntoReclamoDto(Integer numero, EstadoReclamo estado, String tipo, double latitud, double longitud) {
}
