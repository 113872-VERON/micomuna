package com.micomuna.dtos;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;

public record UsuarioDto(Long id, String nombre, String correo, Rol rol, String comunaSlug) {

	public static UsuarioDto desde(Usuario usuario, String comunaSlug) {
		return new UsuarioDto(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getRol(), comunaSlug);
	}
}
