package com.micomuna.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

// Mecanismo común de aislamiento entre comunas: la comuna con la que se trabaja sale SIEMPRE del usuario
// autenticado (el token, ya contrastado con la base), nunca de la URL, de un parámetro ni del cuerpo.
// Los servicios y repositorios de negocio filtran por id() en cada consulta, por ejemplo
// usuarios.findByIdAndComunaId(id, comunaActual.id()), y un recurso de otra comuna responde 404 como si no existiera.
@Component
public class ComunaActual {

	// Lanza AccessDeniedException (403) si no hay sesión o si el usuario no pertenece a una comuna (el superadmin).
	public Long id() {
		Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario
				&& usuario.comunaId() != null) {
			return usuario.comunaId();
		}
		throw new AccessDeniedException("El usuario no pertenece a ninguna comuna");
	}
}
