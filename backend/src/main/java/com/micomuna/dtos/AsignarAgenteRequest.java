package com.micomuna.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AsignarAgenteRequest(@NotNull(message = "Elegí el agente") @Positive(message = "Elegí un agente válido") Long idAgente) {
}
