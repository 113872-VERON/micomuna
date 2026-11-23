package com.micomuna.dtos;

import java.util.List;

public record ResultadoEnLoteDto(List<Integer> aplicados, List<OmitidoDto> omitidos) {
}
