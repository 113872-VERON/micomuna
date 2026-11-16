package com.micomuna.dtos;

import java.util.Locale;

import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;
import com.micomuna.services.SlugUtil;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearComunaRequest(
		@NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "El nombre no puede superar los 100 caracteres") String nombre,
		@NotNull(message = "La provincia es obligatoria") Provincia provincia,
		@Schema(description = "Enlace de la comuna. Si falta, se arma con el nombre y la provincia (salsipuedes-cordoba). No se puede cambiar después.") @Pattern(regexp = SlugUtil.EXPRESION, message = "El enlace solo puede tener minúsculas, números y guiones") @Size(max = SlugUtil.MAXIMO, message = "El enlace no puede superar los 120 caracteres") String slug,
		@Schema(description = "Color de la paleta. Si falta, CELESTE.") ColorComuna color,
		@Size(max = 80, message = "El departamento no puede superar los 80 caracteres") String departamento,
		@NotBlank(message = "El correo del administrador es obligatorio") @Email(message = "El correo del administrador no es válido") @Size(max = 254, message = "El correo no puede superar los 254 caracteres") String adminCorreo,
		@NotBlank(message = "El nombre del administrador es obligatorio") @Size(max = 100, message = "El nombre del administrador no puede superar los 100 caracteres") String adminNombre) {

	// Se normaliza antes de validar: un enlace en blanco es lo mismo que no mandarlo, y el correo va en minúsculas.
	public CrearComunaRequest {
		nombre = recortar(nombre);
		slug = slug == null || slug.isBlank() ? null : slug.trim().toLowerCase(Locale.ROOT);
		departamento = departamento == null || departamento.isBlank() ? null : departamento.trim();
		adminCorreo = adminCorreo == null ? null : adminCorreo.trim().toLowerCase(Locale.ROOT);
		adminNombre = recortar(adminNombre);
	}

	private static String recortar(String texto) {
		return texto == null ? null : texto.trim();
	}
}
