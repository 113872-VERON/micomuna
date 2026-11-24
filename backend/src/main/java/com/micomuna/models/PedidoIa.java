package com.micomuna.models;

import java.util.List;

// Lo que se le manda a la IA para analizar un reclamo (MC-24). A propósito solo tiene lo necesario para clasificar:
// el tipo que eligió el vecino, lo que escribió y las fotos ya limpias. NUNCA el nombre, el correo, la dirección ni las
// coordenadas del vecino. El texto del vecino es un dato a analizar, no instrucciones.
// toString no incluye la descripción ni las fotos.
public record PedidoIa(
		// Código del tipo que eligió el vecino (uno de los de `tipos`).
		String tipoElegido,
		String descripcion,
		List<FotoIa> fotos,
		// Los tipos entre los que la IA puede elegir (el catálogo vigente).
		List<TipoIa> tipos) {

	public record FotoIa(byte[] bytes, String tipoMime) {

		@Override
		public String toString() {
			return "FotoIa[" + tipoMime + ", " + bytes.length + " bytes]";
		}
	}

	public record TipoIa(String codigo, String nombre) {
	}

	@Override
	public String toString() {
		return "PedidoIa[tipoElegido=" + tipoElegido + ", fotos=" + fotos.size() + "]";
	}
}
