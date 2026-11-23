package com.micomuna.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.micomuna.dtos.CambioDeEstadoDto;
import com.micomuna.dtos.CambioDeEstadoPanelDto;
import com.micomuna.entities.ReclamoEstado;

// El historial se agrega y no se cambia ni se borra: por eso solo se expone save y la lectura.
public interface ReclamoEstadoRepository extends Repository<ReclamoEstado, Long> {

	ReclamoEstado save(ReclamoEstado cambio);

	// Solo estado y fecha (nunca el usuario ni la nota interna), del más viejo al más nuevo; el id desempata.
	@Query("select new com.micomuna.dtos.CambioDeEstadoDto(e.estado, e.cambiadoEn) from ReclamoEstado e "
			+ "where e.reclamo.id = :idReclamo order by e.cambiadoEn, e.id")
	List<CambioDeEstadoDto> historialDe(@Param("idReclamo") Long idReclamo);

	// Para el personal: con quién lo hizo (nombre) y la nota interna. El usuario puede faltar (cambio del sistema).
	@Query("select new com.micomuna.dtos.CambioDeEstadoPanelDto(e.estadoAnterior, e.estado, e.cambiadoEn, u.nombre, "
			+ "e.observacion) from ReclamoEstado e left join e.usuario u where e.reclamo.id = :idReclamo "
			+ "order by e.cambiadoEn, e.id")
	List<CambioDeEstadoPanelDto> historialParaElPersonal(@Param("idReclamo") Long idReclamo);
}
