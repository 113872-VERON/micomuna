package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.Repository;

import com.micomuna.entities.TipoReclamo;

// Solo lectura: el catálogo lo carga la migración V5. Por eso no extiende JpaRepository.
public interface TipoReclamoRepository extends Repository<TipoReclamo, Long> {

	List<TipoReclamo> findAllByOrderByOrdenAsc();

	Optional<TipoReclamo> findByCodigo(String codigo);
}
