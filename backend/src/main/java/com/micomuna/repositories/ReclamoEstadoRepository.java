package com.micomuna.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.micomuna.dtos.CambioDeEstadoDto;
import com.micomuna.entities.ReclamoEstado;

// El historial se agrega y no se cambia ni se borra: por eso solo se expone save y la lectura.
public interface ReclamoEstadoRepository extends Repository<ReclamoEstado, Long> {

	ReclamoEstado save(ReclamoEstado cambio);

	// Solo estado y fecha (nunca el usuario, el agente ni la nota interna), del más viejo al más nuevo; el id desempata.
	// Las filas donde el estado no cambió (una reasignación ASIGNADO -> ASIGNADO) no se muestran: para el vecino no pasó nada nuevo.
	@Query("select new com.micomuna.dtos.CambioDeEstadoDto(e.estado, e.cambiadoEn) from ReclamoEstado e "
			+ "where e.reclamo.id = :idReclamo and (e.estadoAnterior is null or e.estadoAnterior <> e.estado) order by e.cambiadoEn, e.id")
	List<CambioDeEstadoDto> historialDe(@Param("idReclamo") Long idReclamo);
}
