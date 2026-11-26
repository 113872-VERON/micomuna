package com.micomuna.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Lo único que cada persona puede cambiar de su cuenta desde "Mi cuenta" es el nombre. El correo, el rol y la comuna
// no están en este pedido a propósito: si el cliente los manda, se ignoran.
public record ActualizarMisDatosRequest(
		// \p{C} es la categoría Unicode de control, formato (RTL, ancho cero), surrogates y sin asignar: no es una lista
		// de letras permitidas, así que siguen valiendo las tildes, la ñ, los apóstrofes y los guiones.
		@NotBlank(message = "El nombre es obligatorio") @Size(max = 100, message = "El nombre no puede superar los 100 caracteres") @Pattern(regexp = "^[^\\p{C}]*$", message = "El nombre tiene caracteres no permitidos") String nombre) {

	// Igual que en el registro y el alta de personal: el nombre se guarda sin espacios al principio ni al final.
	public ActualizarMisDatosRequest {
		nombre = nombre == null ? null : nombre.trim();
	}
}
