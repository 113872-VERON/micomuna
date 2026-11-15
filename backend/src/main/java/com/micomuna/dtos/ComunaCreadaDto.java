package com.micomuna.dtos;

// Nadie recibe ni conoce una clave: el administrador elige la suya con la invitación por correo.
// invitacionEnviada = false significa que la comuna se creó pero el correo no salió (se puede reenviar).
public record ComunaCreadaDto(ComunaAdminDto comuna, String adminCorreo, boolean invitacionEnviada) {
}
