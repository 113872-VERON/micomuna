package com.micomuna.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.micomuna.constants.Bloqueo;
import com.micomuna.constants.Prioridades;
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
				coalesce((select max(e.cambiadoEn) from ReclamoEstado e where e.reclamo.id = r.id and (e.estadoAnterior is null or e.estadoAnterior <> e.estado)), r.creadoEn),
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

	// "Mis reclamos" del agente (MC-20), grupo PARA_HACER: los suyos en ASIGNADO o EN_REPARACION, del más viejo al más
	// nuevo. Una sola consulta con la comuna y el agente; la fecha del último cambio sale de una subconsulta y no se lee
	// ninguna imagen ni el vecino.
	@Query(value = """
			select new com.micomuna.dtos.ReclamoAgenteDto(r.numero, t.codigo, t.nombre, r.estado, r.direccion, r.referencia,
				r.latitud, r.longitud, r.creadoEn,
				coalesce((select max(e.cambiadoEn) from ReclamoEstado e where e.reclamo.id = r.id and (e.estadoAnterior is null or e.estadoAnterior <> e.estado)), r.creadoEn),
				r.prioridad, """ + Prioridades.SUGERIDA + ") " + """
			from Reclamo r join r.tipo t
			where r.comuna.id = :idComuna and r.agente.id = :idAgente
				and r.estado in (com.micomuna.enums.EstadoReclamo.ASIGNADO, com.micomuna.enums.EstadoReclamo.EN_REPARACION)
			order by r.creadoEn, r.id
			""",
			countQuery = """
			select count(r) from Reclamo r where r.comuna.id = :idComuna and r.agente.id = :idAgente
				and r.estado in (com.micomuna.enums.EstadoReclamo.ASIGNADO, com.micomuna.enums.EstadoReclamo.EN_REPARACION)
			""")
	Page<com.micomuna.dtos.ReclamoAgenteDto> paraHacerDelAgente(@Param("idComuna") Long idComuna,
			@Param("idAgente") Long idAgente, Pageable pagina);

	// Grupo RESUELTOS: los que este agente tiene en RESUELTO, del resuelto más reciente al más viejo. La fecha de
	// resolución es la de la última fila del historial que pasó a RESUELTO (si se reabrió y se resolvió de nuevo, la más reciente).
	@Query(value = """
			select new com.micomuna.dtos.ReclamoAgenteDto(r.numero, t.codigo, t.nombre, r.estado, r.direccion, r.referencia,
				r.latitud, r.longitud, r.creadoEn,
				coalesce((select max(e.cambiadoEn) from ReclamoEstado e where e.reclamo.id = r.id
					and e.estado = com.micomuna.enums.EstadoReclamo.RESUELTO), r.creadoEn),
				r.prioridad, """ + Prioridades.SUGERIDA + ") " + """
			from Reclamo r join r.tipo t
			where r.comuna.id = :idComuna and r.agente.id = :idAgente and r.estado = com.micomuna.enums.EstadoReclamo.RESUELTO
			order by coalesce((select max(e2.cambiadoEn) from ReclamoEstado e2 where e2.reclamo.id = r.id
					and e2.estado = com.micomuna.enums.EstadoReclamo.RESUELTO), r.creadoEn) desc, r.id desc
			""",
			countQuery = """
			select count(r) from Reclamo r where r.comuna.id = :idComuna and r.agente.id = :idAgente
				and r.estado = com.micomuna.enums.EstadoReclamo.RESUELTO
			""")
	Page<com.micomuna.dtos.ReclamoAgenteDto> resueltosDelAgente(@Param("idComuna") Long idComuna,
			@Param("idAgente") Long idAgente, Pageable pagina);

	// Un reclamo del agente por su número: comuna, número y agente en la misma consulta. De otro agente, de otra comuna,
	// inexistente o DESCARTADO, el resultado es el mismo (vacío).
	@Query("""
			select r from Reclamo r join fetch r.tipo
			where r.comuna.id = :idComuna and r.numero = :numero and r.agente.id = :idAgente
				and r.estado <> com.micomuna.enums.EstadoReclamo.DESCARTADO
			""")
	Optional<Reclamo> buscarDelAgente(@Param("idComuna") Long idComuna, @Param("numero") Integer numero,
			@Param("idAgente") Long idAgente);

	// Los vecinos de los reclamos descartados como DUPLICADO de este (MC-22): id, correo, nombre y número de cada uno, para
	// avisarles cuando el original cambia de estado.
	@Query("""
			select v.id, v.correo, v.nombre, r.numero from Reclamo r join r.vecino v
			where r.comuna.id = :idComuna and r.reclamoOriginal.id = :idOriginal and v.activo = true
				and r.estado = com.micomuna.enums.EstadoReclamo.DESCARTADO
				and r.motivoDescarte = com.micomuna.enums.MotivoDescarte.DUPLICADO
			order by r.numero
			""")
	List<Object[]> duplicadosDe(@Param("idComuna") Long idComuna, @Param("idOriginal") Long idOriginal);

	// Para vincular un duplicado con su original: siempre dentro de la comuna.
	Optional<Reclamo> findByComunaIdAndNumero(Long idComuna, Integer numero);

	// Toma la fila del reclamo hasta que termine la transacción (SELECT ... FOR UPDATE WAIT 3, ver Bloqueo), para que
	// dos cambios de estado sobre el mismo reclamo se hagan de a uno. Sin joins a propósito: Oracle no deja bloquear
	// con uniones externas. Filtra por comuna: un reclamo de otra comuna es como si no existiera.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = Bloqueo.HINT_ESPERA, value = Bloqueo.ESPERA_MAXIMA_MS))
	@Query("select r from Reclamo r where r.comuna.id = :idComuna and r.numero = :numero")
	Optional<Reclamo> bloquearPorNumero(@Param("idComuna") Long idComuna, @Param("numero") Integer numero);

	// Candidatos a duplicado (MC-26): misma comuna y tipo, abiertos, con ubicación dentro de una caja de coordenadas y distintos
	// del propio. La caja solo descarta rápido; la distancia exacta la calcula PosiblesDuplicadosService.
	@Query("""
			select new com.micomuna.models.CandidatoCercano(r.id, r.numero, r.estado, r.creadoEn, r.direccion, r.descripcion,
				r.latitud, r.longitud)
			from Reclamo r
			where r.comuna.id = :idComuna and r.tipo.id = :idTipo and r.id <> :idPropio
				and r.estado in (com.micomuna.enums.EstadoReclamo.RECIBIDO, com.micomuna.enums.EstadoReclamo.ASIGNADO,
					com.micomuna.enums.EstadoReclamo.EN_REPARACION)
				and r.latitud is not null and r.longitud is not null
				and r.latitud between :latMin and :latMax and r.longitud between :lngMin and :lngMax
			order by r.creadoEn, r.id
			""")
	List<com.micomuna.models.CandidatoCercano> candidatosCercanos(@Param("idComuna") Long idComuna,
			@Param("idTipo") Long idTipo, @Param("idPropio") Long idPropio, @Param("latMin") java.math.BigDecimal latMin,
			@Param("latMax") java.math.BigDecimal latMax, @Param("lngMin") java.math.BigDecimal lngMin,
			@Param("lngMax") java.math.BigDecimal lngMax);

	// Marca "no es duplicado" sin pasar por @PreUpdate: así no se mueve actualizado_en (no es un cambio del reclamo).
	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Reclamo r set r.usuarioNoDuplicado.id = :idUsuario, r.noDuplicadoEn = :cuando where r.id = :id")
	int marcarNoDuplicado(@Param("id") Long id, @Param("idUsuario") Long idUsuario, @Param("cuando") Instant cuando);

	// Fija la prioridad del personal (MC-27) sin pasar por @PreUpdate: así no se mueve actualizado_en (la decisión queda en
	// prioridad_fijada_en y, si cambió la prioridad efectiva, en el historial). Prioridad, quién y cuándo van juntos (V15).
	@org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update Reclamo r set r.prioridad = :prioridad, r.usuarioPrioridad.id = :idUsuario, r.prioridadFijadaEn = :cuando where r.id = :id")
	int fijarPrioridad(@Param("id") Long id, @Param("prioridad") com.micomuna.enums.PrioridadIa prioridad,
			@Param("idUsuario") Long idUsuario, @Param("cuando") Instant cuando);

	// El agente asignado a un reclamo de la comuna (vacío si no tiene o el reclamo no existe).
	@Query("select a from Reclamo r join r.agente a where r.comuna.id = :idComuna and r.numero = :numero")
	Optional<com.micomuna.entities.Usuario> agenteDe(@Param("idComuna") Long idComuna, @Param("numero") Integer numero);
}
