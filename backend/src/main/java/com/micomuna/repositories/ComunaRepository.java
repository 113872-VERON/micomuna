package com.micomuna.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.micomuna.entities.Comuna;

public interface ComunaRepository extends JpaRepository<Comuna, Long> {

	Optional<Comuna> findBySlug(String slug);

	boolean existsBySlug(String slug);
}
