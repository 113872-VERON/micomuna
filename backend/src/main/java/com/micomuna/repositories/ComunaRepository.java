package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.micomuna.constants.Bloqueo;
import com.micomuna.dtos.ComunaAdminDto;
import com.micomuna.entities.Comuna;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface ComunaRepository extends JpaRepository<Comuna, Long> {

	Optional<Comuna> findBySlug(String slug);

	boolean existsBySlug(String slug);

	// Arma los datos directamente, sin traer la imagen del logo (hasta 512 KB por comuna).
	@Query("""
			select new com.micomuna.dtos.ComunaAdminDto(c.id, c.nombre, c.provincia, c.slug, c.activa, c.color,
				c.departamento, case when c.logoTipo is null then false else true end)
			from Comuna c order by c.nombre, c.id
			""")
	List<ComunaAdminDto> listarParaAdmin();

	// Bloquea la fila de la comuna hasta que termine la transacción (SELECT ... FOR UPDATE). Sirve para que dos
	// cambios simultáneos sobre los administradores de una misma comuna se hagan de a uno.
	// Si otra transacción la tiene, espera hasta 3 segundos (Bloqueo.ESPERA_MAXIMA_MS).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = Bloqueo.HINT_ESPERA, value = Bloqueo.ESPERA_MAXIMA_MS))
	@Query("select c from Comuna c where c.id = :id")
	Optional<Comuna> bloquear(@Param("id") Long id);
}
