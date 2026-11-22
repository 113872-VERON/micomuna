package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

public record DireccionDto(
		@Schema(description = "Dirección aproximada, por ejemplo \"Av. San Martín 120, Agua de Oro\"", maxLength = 300) String direccion) {
}
