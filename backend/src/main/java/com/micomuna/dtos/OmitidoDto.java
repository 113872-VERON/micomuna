package com.micomuna.dtos;

import com.micomuna.enums.CodigoOmitido;

import io.swagger.v3.oas.annotations.media.Schema;

public record OmitidoDto(
		Integer numero,
		CodigoOmitido codigo,
		@Schema(description = "Texto para mostrar, por ejemplo \"El n.º 39 ya está cerrado.\"") String motivo) {
}
