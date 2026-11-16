package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.micomuna.entities.CodigoRecuperacion;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

	Optional<CodigoRecuperacion> findByCodigoHash(String codigoHash);

	@Query("select c from CodigoRecuperacion c where c.usuario.id = :idUsuario and c.usado = false")
	List<CodigoRecuperacion> findPendientesDelUsuario(@Param("idUsuario") Long idUsuario);
}
