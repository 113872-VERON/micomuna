package com.micomuna.repositories;

import org.springframework.data.repository.Repository;

import com.micomuna.entities.ReclamoEstado;

// El historial se agrega y no se cambia ni se borra: por eso solo se expone save.
public interface ReclamoEstadoRepository extends Repository<ReclamoEstado, Long> {

	ReclamoEstado save(ReclamoEstado cambio);
}
