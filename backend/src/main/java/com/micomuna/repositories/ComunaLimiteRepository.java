package com.micomuna.repositories;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.micomuna.entities.ComunaLimite;

// A propósito no extiende JpaRepository: la fila la crea y la borra la comuna, acá solo se lee y se cambia el límite.
public interface ComunaLimiteRepository extends Repository<ComunaLimite, Long> {

	Optional<ComunaLimite> findById(Long id);

	// Cambia el límite sin leer antes el anterior. Devuelve cuántas filas cambió (0 si la comuna no existe).
	// Vacía la sesión de Hibernate para que una Comuna ya cargada no siga diciendo que no tiene límite.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update ComunaLimite l set l.limite = :limite, l.limiteCargadoEn = :cargadoEn where l.id = :id")
	int guardar(@Param("id") Long id, @Param("limite") String limite, @Param("cargadoEn") Instant cargadoEn);
}
