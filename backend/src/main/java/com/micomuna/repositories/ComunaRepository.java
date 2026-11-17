package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.micomuna.dtos.ComunaAdminDto;
import com.micomuna.entities.Comuna;

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
}
