package com.micomuna.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.micomuna.constants.Bloqueo;
import com.micomuna.dtos.MiReclamoResumenDto;
import com.micomuna.entities.Reclamo;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

// Las consultas de negocio van siempre por comuna (ComunaActual) y, las del vecino, también por vecino.
public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

	// Reclamos de un vecino creados desde un momento dado (límite por hora del alta).
	// Filtra por comuna, vecino y fecha, en el orden del índice ix_reclamo_comuna_vecino_fecha de V5.
	@Query("select count(r) from Reclamo r where r.comuna.id = :idComuna and r.vecino.id = :idVecino and r.creadoEn > :desde")
	long contarDelVecinoDesde(@Param("idComuna") Long idComuna, @Param("idVecino") Long idVecino,
			@Param("desde") Instant desde);

	// "Mis reclamos" (MC-17): una sola consulta arma cada fila completa, con el último cambio de estado y la foto de
	// la miniatura como subconsultas. Así no hay una consulta extra por reclamo (N+1) y tampoco se lee ninguna imagen:
	// de la foto solo se pide el id. Sale del más nuevo al más viejo (id desempata).
	// La miniatura es la foto del VECINO con el orden más bajo; las del trabajo no cuentan.
	@Query(value = """
			select new com.micomuna.dtos.MiReclamoResumenDto(r.numero, t.codigo, t.nombre, r.estado, r.descripcion,
				r.direccion, r.creadoEn,
				coalesce((select max(e.cambiadoEn) from ReclamoEstado e where e.reclamo.id = r.id), r.creadoEn),
				(select f.id from ReclamoFoto f where f.reclamo.id = r.id
					and f.origen = com.micomuna.enums.OrigenFoto.VECINO
					and f.orden = (select min(f2.orden) from ReclamoFoto f2
						where f2.reclamo.id = r.id and f2.origen = com.micomuna.enums.OrigenFoto.VECINO)))
			from Reclamo r join r.tipo t
			where r.comuna.id = :idComuna and r.vecino.id = :idVecino
			order by r.creadoEn desc, r.id desc
			""",
			countQuery = "select count(r) from Reclamo r where r.comuna.id = :idComuna and r.vecino.id = :idVecino")
	Page<MiReclamoResumenDto> misReclamos(@Param("idComuna") Long idComuna, @Param("idVecino") Long idVecino,
			Pageable pagina);

	// Un reclamo del vecino por su número, en una sola consulta con comuna, número y vecino: si es de otro vecino,
	// de otra comuna o no existe, el resultado es el mismo (vacío). Trae el tipo y, si es duplicado, el original.
	@Query("""
			select r from Reclamo r join fetch r.tipo left join fetch r.reclamoOriginal
			where r.comuna.id = :idComuna and r.numero = :numero and r.vecino.id = :idVecino
			""")
	Optional<Reclamo> buscarDelVecino(@Param("idComuna") Long idComuna, @Param("numero") Integer numero,
			@Param("idVecino") Long idVecino);

	// Para vincular un duplicado con su original: siempre dentro de la comuna.
	Optional<Reclamo> findByComunaIdAndNumero(Long idComuna, Integer numero);

	// Toma la fila del reclamo hasta que termine la transacción (SELECT ... FOR UPDATE WAIT 3, ver Bloqueo), para que
	// dos cambios de estado sobre el mismo reclamo se hagan de a uno. Sin joins a propósito: Oracle no deja bloquear
	// con uniones externas. Filtra por comuna: un reclamo de otra comuna es como si no existiera.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = Bloqueo.HINT_ESPERA, value = Bloqueo.ESPERA_MAXIMA_MS))
	@Query("select r from Reclamo r where r.comuna.id = :idComuna and r.numero = :numero")
	Optional<Reclamo> bloquearPorNumero(@Param("idComuna") Long idComuna, @Param("numero") Integer numero);
}
