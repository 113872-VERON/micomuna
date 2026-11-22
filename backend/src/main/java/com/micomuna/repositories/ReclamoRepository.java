package com.micomuna.repositories;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.micomuna.entities.Reclamo;

// Las consultas de negocio (listados, detalle) llegan con MC-17 y MC-18 y van siempre por id Y comuna (ComunaActual).
public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

	// Reclamos de un vecino creados desde un momento dado (límite por hora del alta).
	// Filtra por comuna, vecino y fecha, en el orden del índice ix_reclamo_comuna_vecino_fecha de V5.
	@Query("select count(r) from Reclamo r where r.comuna.id = :idComuna and r.vecino.id = :idVecino and r.creadoEn > :desde")
	long contarDelVecinoDesde(@Param("idComuna") Long idComuna, @Param("idVecino") Long idVecino,
			@Param("desde") Instant desde);
}
