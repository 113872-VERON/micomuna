package com.micomuna.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.micomuna.entities.CodigoRecuperacion;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Long> {

	Optional<CodigoRecuperacion> findByCodigoHash(String codigoHash);
}
