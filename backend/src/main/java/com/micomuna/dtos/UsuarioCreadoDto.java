package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;

// invitacionEnviada = false significa que el usuario se creó pero el correo no salió (se puede reenviar).
@Schema(description = "invitacionEnviada = false significa que el usuario se creó pero el correo no salió (se puede reenviar).")
public record UsuarioCreadoDto(UsuarioComunaDto usuario, boolean invitacionEnviada) {
}
