package com.micomuna.dtos;

import com.micomuna.entities.Comuna;

public record ComunaDto(String nombre, String provincia, String departamento, String slug, boolean tieneLogo,
		String color, String telefono, String whatsapp) {

	public static ComunaDto desde(Comuna comuna) {
		return new ComunaDto(
				comuna.getNombre(),
				comuna.getProvincia().getNombre(),
				comuna.getDepartamento(),
				comuna.getSlug(),
				comuna.tieneLogo(),
				comuna.getColor().name(),
				comuna.getTelefono(),
				comuna.getWhatsapp());
	}
}
