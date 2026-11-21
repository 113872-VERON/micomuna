package com.micomuna.dtos;

import java.time.Instant;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoUsuario;
import com.micomuna.enums.Rol;

import io.swagger.v3.oas.annotations.media.Schema;

// Un usuario de la comuna, para el panel del administrador. Nunca trae la clave ni su hash.
@Schema(description = "Un usuario de la comuna, para el panel del administrador. Nunca trae la clave.")
public record UsuarioComunaDto(
		Long id,
		String nombre,
		String correo,
		@Schema(allowableValues = { "VECINO", "ADMINISTRATIVO", "AGENTE", "ADMIN_COMUNA" }) Rol rol,
		@Schema(description = "PENDIENTE: todavía no eligió su contraseña. INACTIVO: desactivado por el administrador (tiene prioridad sobre PENDIENTE). ACTIVO: el resto.") EstadoUsuario estado,
		Instant creadoEn) {

	public static UsuarioComunaDto desde(Usuario u) {
		return new UsuarioComunaDto(u.getId(), u.getNombre(), u.getCorreo(), u.getRol(), estadoDe(u), u.getCreadoEn());
	}

	// Desactivado tiene prioridad: un pendiente desactivado se muestra como INACTIVO.
	public static EstadoUsuario estadoDe(Usuario u) {
		if (!u.isActivo()) {
			return EstadoUsuario.INACTIVO;
		}
		return u.getClaveHash() == null ? EstadoUsuario.PENDIENTE : EstadoUsuario.ACTIVO;
	}
}
