package com.micomuna.security;

import com.micomuna.enums.Rol;

// Lo que viaja dentro del token. comunaId es nulo solo para el superadmin.
public record DatosToken(Long usuarioId, Rol rol, Long comunaId) {
}
