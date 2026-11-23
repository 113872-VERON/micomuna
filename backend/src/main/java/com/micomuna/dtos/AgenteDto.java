package com.micomuna.dtos;

import com.micomuna.entities.Usuario;

// Un agente de la comuna: solo su id y su nombre (nunca el correo).
public record AgenteDto(Long id, String nombre) {

	public static AgenteDto desde(Usuario u) {
		return u == null ? null : new AgenteDto(u.getId(), u.getNombre());
	}
}
