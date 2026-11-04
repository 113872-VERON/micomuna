package com.micomuna.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Endpoint solo para las pruebas: sirve para comprobar que el filtro deja pasar o frena.
@RestController
class RutaProtegidaDePrueba {

	@GetMapping("/api/prueba/yo")
	String yo(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return usuario.id() + ":" + usuario.rol() + ":" + usuario.comunaId();
	}
}
