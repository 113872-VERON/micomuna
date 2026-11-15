package com.micomuna.dtos;

// Nadie recibe ni conoce una clave: el administrador elige la suya con la invitación por correo.
public record ComunaCreadaDto(ComunaAdminDto comuna, String adminCorreo) {
}
