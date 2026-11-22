package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.entities.Comuna;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;

import io.swagger.v3.oas.annotations.media.Schema;

// Lo que ve el superadmin de cada comuna, activa o no. Nunca lleva la imagen del logo ni la figura del límite.
public record ComunaAdminDto(Long id, String nombre, String provincia, String slug, boolean activa, String color,
		String departamento, boolean tieneLogo,
		@Schema(description = "Si la comuna tiene cargado su límite geográfico. Sin límite, los vecinos pueden reportar desde cualquier lugar.") boolean tieneLimite,
		@Schema(description = "Cuándo se cargó el límite (vacío si no tiene)") Instant limiteCargadoEn) {

	// Lo usa la consulta de la lista (ComunaRepository.listarParaAdmin), que no trae el logo ni el límite de la base.
	public ComunaAdminDto(Long id, String nombre, Provincia provincia, String slug, boolean activa,
			ColorComuna color, String departamento, boolean tieneLogo, boolean tieneLimite, Instant limiteCargadoEn) {
		this(id, nombre, provincia.getNombre(), slug, activa, color.name(), departamento, tieneLogo, tieneLimite,
				limiteCargadoEn);
	}

	public static ComunaAdminDto desde(Comuna c) {
		return new ComunaAdminDto(c.getId(), c.getNombre(), c.getProvincia(), c.getSlug(), c.isActiva(), c.getColor(),
				c.getDepartamento(), c.tieneLogo(), c.tieneLimite(), c.getLimiteCargadoEn());
	}
}
