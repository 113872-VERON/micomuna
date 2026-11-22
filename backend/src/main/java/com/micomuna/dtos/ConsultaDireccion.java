package com.micomuna.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

// Parametros de GET /api/ubicacion/direccion: los dos obligatorios y en rango (400 VALIDACION).
public record ConsultaDireccion(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "double", minimum = "-90", maximum = "90")
		@NotNull(message = "Falta la latitud")
		@DecimalMin(value = "-90", message = "La latitud tiene que estar entre -90 y 90")
		@DecimalMax(value = "90", message = "La latitud tiene que estar entre -90 y 90") Double latitud,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "double", minimum = "-180", maximum = "180")
		@NotNull(message = "Falta la longitud")
		@DecimalMin(value = "-180", message = "La longitud tiene que estar entre -180 y 180")
		@DecimalMax(value = "180", message = "La longitud tiene que estar entre -180 y 180") Double longitud) {
}
