package com.micomuna.dtos;

import java.util.List;

public record PaginaReclamosPanelDto(List<ReclamoPanelDto> reclamos, int pagina, int tamanio, long total) {
}
