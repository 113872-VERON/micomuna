package com.micomuna.dtos;

import com.micomuna.entities.TipoReclamo;

import io.swagger.v3.oas.annotations.media.Schema;

public record TipoReclamoDto(
		@Schema(allowableValues = { "BACHE", "LUZ", "BASURA", "ARBOL", "AGUA", "OTRO" }) String codigo,
		@Schema(description = "Nombre para mostrar, por ejemplo \"Bache o calle rota\"") String nombre,
		@Schema(description = "Consejo para sacar la foto de este tipo de problema") String consejoFoto) {

	public static TipoReclamoDto desde(TipoReclamo t) {
		return new TipoReclamoDto(t.getCodigo(), t.getNombre(), t.getConsejoFoto());
	}
}
