package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// Es un texto y no el enum a propósito: un valor que no es ALTA, MEDIA ni BAJA tiene que responder 400 VALIDACION (como
// dice el contrato) y no el 400 genérico que da un enum que no se puede leer.
public record CambiarPrioridadRequest(
		@Schema(allowableValues = { "ALTA", "MEDIA", "BAJA" })
		@NotBlank(message = "Elegí la prioridad")
		@Pattern(regexp = "ALTA|MEDIA|BAJA", message = "La prioridad tiene que ser ALTA, MEDIA o BAJA") String prioridad) {
}
