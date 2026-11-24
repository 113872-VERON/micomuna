package com.micomuna.models;

// Aviso interno: se creó un reclamo (MC-24). Lo publica ReclamoService dentro de la transacción del alta y lo atiende
// AnalisisIaService recién después del commit. Lleva solo datos sueltos (nada de entidades ni fotos: las fotos se leen de
// la base cuando le toca el turno, así la cola de análisis no guarda imágenes en memoria).
// No lleva nada del vecino (ni nombre, ni correo, ni dirección, ni coordenadas). La descripción es texto del vecino:
// por eso toString no la incluye y este registro nunca debe escribirse en los logs completo.
public record ReclamoRegistrado(
		Long comunaId,
		String comunaSlug,
		Long reclamoId,
		Integer numero,
		String tipoCodigo,
		String descripcion) {

	@Override
	public String toString() {
		return "ReclamoRegistrado[comuna=" + comunaSlug + ", numero=" + numero + "]";
	}
}
