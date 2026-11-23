package com.micomuna.models;

import java.time.Instant;
import java.util.List;

import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;

// Aviso interno: un reclamo cambió de estado (MC-22). Lo publica CambioDeEstadoService dentro de la transacción y lo
// atiende AvisosDeReclamoService recién después del commit. Lleva solo datos sueltos (nada de entidades): cuando se
// atiende, la transacción ya terminó. No lleva nombres del personal ni del agente.
public record CambioDeEstadoRegistrado(
		String comunaNombre,
		String comunaSlug,
		String comunaTelefono,
		String comunaWhatsapp,
		Integer numero,
		String vecinoCorreo,
		String vecinoNombre,
		EstadoReclamo estado,
		Instant fecha,
		MotivoDescarte motivoDescarte,
		String observacionDescarte,
		String trabajoRealizado,
		List<VecinoDeDuplicados> duplicados) {

	// Un vecino que reportó uno o más reclamos descartados como duplicado de este (sus propios números).
	public record VecinoDeDuplicados(String correo, String nombre, List<Integer> numeros) {
	}
}
