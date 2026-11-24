package com.micomuna.repositories;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.micomuna.entities.SugerenciaIa;

// Las sugerencias de la IA se crean y no se cambian (MC-24). Mostrarlas es de MC-25 y MC-27.
public interface SugerenciaIaRepository extends Repository<SugerenciaIa, Long> {

	SugerenciaIa save(SugerenciaIa sugerencia);

	// Las filas de un reclamo, de la más nueva a la más vieja (la vigente es la primera).
	List<SugerenciaIa> findByIdReclamoOrderByCreadaEnDescIdDesc(Long idReclamo);

	// Tope diario: los análisis de la comuna desde `desde` (el comienzo del día argentino). Cuentan los que llamaron al
	// proveedor (HECHA y ERROR, que son los que cuestan); las OMITIDA no. Usa el índice (id_comuna, creada_en).
	@Query("""
			select count(s) from SugerenciaIa s
			where s.idComuna = :idComuna and s.creadaEn >= :desde
				and s.estado in (com.micomuna.enums.EstadoSugerenciaIa.HECHA, com.micomuna.enums.EstadoSugerenciaIa.ERROR)
			""")
	long contarAnalisisDesde(@Param("idComuna") Long idComuna, @Param("desde") Instant desde);

	// Tope global diario: lo mismo pero de todas las comunas juntas (la tabla tiene una fila por análisis, así que un día
	// son pocas filas).
	@Query("""
			select count(s) from SugerenciaIa s
			where s.creadaEn >= :desde
				and s.estado in (com.micomuna.enums.EstadoSugerenciaIa.HECHA, com.micomuna.enums.EstadoSugerenciaIa.ERROR)
			""")
	long contarAnalisisDeTodasLasComunasDesde(@Param("desde") Instant desde);
}
