package com.micomuna.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.micomuna.entities.ComunaLogo;

// A propósito no extiende JpaRepository: la fila la crea y la borra la comuna, acá solo se lee y se cambia el logo.
public interface ComunaLogoRepository extends Repository<ComunaLogo, Long> {

	Optional<ComunaLogo> findById(Long id);

	// Cambia el logo sin leer antes el anterior. Devuelve cuántas filas cambió (0 si la comuna no existe).
	// Vacía la sesión de Hibernate para que una Comuna ya cargada no siga diciendo que no tiene logo.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ComunaLogo l set l.logo = :logo, l.logoTipo = :tipo where l.id = :id")
	int guardar(@Param("id") Long id, @Param("logo") byte[] logo, @Param("tipo") String tipo);
}
