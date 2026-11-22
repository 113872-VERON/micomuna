package com.micomuna.dtos;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record PaginaMisReclamosDto(
		List<MiReclamoResumenDto> reclamos,
		int pagina,
		int tamanio,
		@Schema(description = "Cantidad total de reclamos del vecino en la comuna") long total) {
}
