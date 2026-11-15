package com.micomuna.dtos;

// false cuando el correo no salió: el superadmin ve el aviso y puede reenviar.
public record InvitacionRespuesta(boolean invitacionEnviada) {
}
