package com.micomuna.security;

import com.micomuna.enums.Rol;

// Principal que queda en el SecurityContext durante la petición.
public record UsuarioAutenticado(Long id, Rol rol, Long comunaId) {
}
