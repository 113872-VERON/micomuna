package com.micomuna.dtos;

import com.micomuna.enums.ColorComuna;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Cambio parcial: lo que no se envía no se toca. El enlace (slug) no se puede cambiar.
public record ActualizarComunaRequest(
		@Size(min = 1, max = 100, message = "El nombre no puede estar vacío ni superar los 100 caracteres") String nombre,
		Boolean activa,
		ColorComuna color,
		@Schema(description = "Un texto vacío borra el departamento.") @Size(max = 80, message = "El departamento no puede superar los 80 caracteres") String departamento,
		@Schema(description = "Solo números, con el código de país 54. Un texto vacío lo borra.", pattern = "^54[0-9]{10,11}$") @Pattern(regexp = Telefonos.EXPRESION_O_VACIO, message = Telefonos.MENSAJE) String telefono,
		@Schema(description = "Solo números, con el código de país 54. Un texto vacío lo borra.", pattern = "^54[0-9]{10,11}$") @Pattern(regexp = Telefonos.EXPRESION_O_VACIO, message = Telefonos.MENSAJE) String whatsapp) {

	public ActualizarComunaRequest {
		nombre = nombre == null ? null : nombre.trim();
		departamento = departamento == null ? null : departamento.trim();
		telefono = Telefonos.normalizar(telefono);
		whatsapp = Telefonos.normalizar(whatsapp);
	}
}
