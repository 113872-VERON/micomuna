package com.micomuna.dtos;

import com.micomuna.entities.Comuna;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;

// Lo que ve el superadmin de cada comuna, activa o no. Nunca lleva la imagen del logo.
public record ComunaAdminDto(Long id, String nombre, String provincia, String slug, boolean activa, String color,
		String departamento, boolean tieneLogo) {

	// Lo usa la consulta de la lista (ComunaRepository.listarParaAdmin), que no trae el logo de la base.
	public ComunaAdminDto(Long id, String nombre, Provincia provincia, String slug, boolean activa,
			ColorComuna color, String departamento, boolean tieneLogo) {
		this(id, nombre, provincia.getNombre(), slug, activa, color.name(), departamento, tieneLogo);
	}

	public static ComunaAdminDto desde(Comuna c) {
		return new ComunaAdminDto(c.getId(), c.getNombre(), c.getProvincia(), c.getSlug(), c.isActiva(), c.getColor(),
				c.getDepartamento(), c.tieneLogo());
	}
}
