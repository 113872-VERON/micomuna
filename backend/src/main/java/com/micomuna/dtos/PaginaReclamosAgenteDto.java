package com.micomuna.dtos;

import java.util.List;

public record PaginaReclamosAgenteDto(List<ReclamoAgenteDto> reclamos, int pagina, int tamanio, long total) {
}
