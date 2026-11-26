package com.micomuna.services;

import org.springframework.stereotype.Service;

import com.micomuna.dtos.PrioridadReclamoDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.enums.EstadoSugerenciaIa;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.repositories.SugerenciaIaRepository;

import lombok.RequiredArgsConstructor;

// Prioridad efectiva de un reclamo (MC-27): la que fijó el personal; si no, la sugerida por la IA en la sugerencia vigente (la
// fila más nueva) si está HECHA; si no, ninguna. La sugerencia nunca se copia al reclamo. Si la vigente es ERROR u OMITIDA no se
// busca una HECHA más vieja: la vigente manda. Las listas calculan lo mismo en SQL (ver constants.Prioridades).
@Service
@RequiredArgsConstructor
public class PrioridadDelReclamo {

	private final SugerenciaIaRepository sugerencias;

	public PrioridadReclamoDto de(Reclamo reclamo) {
		return PrioridadReclamoDto.de(reclamo.getPrioridad(), sugeridaPorLaIa(reclamo));
	}

	// La prioridad de la sugerencia vigente, solo si está HECHA (vacío si no hay sugerencia o falló).
	public PrioridadIa sugeridaPorLaIa(Reclamo reclamo) {
		return sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(reclamo.getId())
				.filter(s -> s.getEstado() == EstadoSugerenciaIa.HECHA).map(SugerenciaIa::getPrioridadSugerida).orElse(null);
	}
}
