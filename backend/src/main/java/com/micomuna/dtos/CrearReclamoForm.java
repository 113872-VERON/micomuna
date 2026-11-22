package com.micomuna.dtos;

import java.util.List;
import java.util.Objects;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Formulario multipart del alta de un reclamo (campos planos más las fotos).
// A propósito no tiene comuna ni vecino: salen siempre del token. Si el pedido los trae, se ignoran.
// Que haya ubicación (latitud y longitud), que el tipo exista, que el punto esté dentro de la comuna y que las fotos sean válidas lo revisa
// ReclamoService, con sus propios códigos de error.
@CoordenadasJuntas
public record CrearReclamoForm(
		@Schema(description = "Código del tipo de problema (ver /api/tipos-reclamo)", requiredMode = Schema.RequiredMode.REQUIRED,
				allowableValues = { "BACHE", "LUZ", "BASURA", "ARBOL", "AGUA", "OTRO" })
		@NotBlank(message = "Elegí el tipo de problema") @Size(max = 20, message = "Elegí uno de los tipos de problema de la lista") String tipo,

		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 500)
		@NotBlank(message = "Contanos qué problema hay") @Size(max = 500, message = "La descripción puede tener hasta 500 caracteres. Acortala un poco") String descripcion,

		@Schema(description = "Va junto con longitud. Se guardan 6 decimales.", requiredMode = Schema.RequiredMode.REQUIRED, format = "double", minimum = "-90", maximum = "90")
		@DecimalMin(value = "-90", message = CoordenadasJuntasValidator.UBICACION_NO_LEIDA)
		@DecimalMax(value = "90", message = CoordenadasJuntasValidator.UBICACION_NO_LEIDA) Double latitud,

		@Schema(description = "Va junto con latitud. Se guardan 6 decimales.", requiredMode = Schema.RequiredMode.REQUIRED, format = "double", minimum = "-180", maximum = "180")
		@DecimalMin(value = "-180", message = CoordenadasJuntasValidator.UBICACION_NO_LEIDA)
		@DecimalMax(value = "180", message = CoordenadasJuntasValidator.UBICACION_NO_LEIDA) Double longitud,

		@Schema(description = "Opcional. Cómo encontrar el lugar, por ejemplo \"frente a la panadería\"", maxLength = 200)
		@Size(max = 200, message = "La referencia puede tener hasta 200 caracteres. Acortala un poco") String referencia,

		@Schema(description = "Opcional. Dirección aproximada del punto, la que devolvió /api/ubicacion/direccion. Es solo informativa: el servidor no la usa para validar nada.", maxLength = 300)
		@Size(max = 300, message = "La dirección puede tener hasta 300 caracteres") String direccion,

		@ArraySchema(maxItems = 3, schema = @Schema(type = "string", format = "binary")) List<MultipartFile> fotos) {

	// Los textos van sin espacios de más; una referencia en blanco es como no mandarla.
	public CrearReclamoForm {
		tipo = tipo == null ? null : tipo.strip();
		descripcion = descripcion == null ? null : descripcion.strip();
		referencia = referencia == null || referencia.isBlank() ? null : referencia.strip();
		direccion = direccion == null || direccion.isBlank() ? null : direccion.strip();
		fotos = fotos == null ? List.of() : fotos.stream().filter(Objects::nonNull).toList();
	}

	public boolean tieneCoordenadas() {
		return latitud != null && longitud != null;
	}
}
