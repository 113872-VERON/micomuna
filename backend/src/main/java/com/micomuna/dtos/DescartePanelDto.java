package com.micomuna.dtos;

import com.micomuna.enums.MotivoDescarte;

public record DescartePanelDto(MotivoDescarte motivo, String observacion, Integer numeroOriginal) {
}
