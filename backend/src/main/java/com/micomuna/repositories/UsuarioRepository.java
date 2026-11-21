package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.micomuna.constants.Bloqueo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByComunaIdAndCorreo(Long idComuna, String correo);

	Optional<Usuario> findByComunaIsNullAndCorreo(String correo);

	// Patrón para todo lo que es de una comuna: se busca siempre por id Y por la comuna del token (ComunaActual).
	Optional<Usuario> findByIdAndComunaId(Long id, Long idComuna);

	// Igual que findByIdAndComunaId, pero deja la fila bloqueada hasta que termine la transacción: dos cambios
	// simultáneos sobre el mismo usuario se hacen de a uno y el segundo parte de lo que guardó el primero.
	// Si otra transacción la tiene, espera hasta 3 segundos (Bloqueo.ESPERA_MAXIMA_MS).
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = Bloqueo.HINT_ESPERA, value = Bloqueo.ESPERA_MAXIMA_MS))
	@Query("select u from Usuario u where u.id = :id and u.comuna.id = :idComuna")
	Optional<Usuario> bloquearPorIdYComuna(@Param("id") Long id, @Param("idComuna") Long idComuna);

	// Todos los usuarios de una comuna. El orden por nombre lo arma el servicio (sin distinguir mayúsculas ni tildes).
	List<Usuario> findByComunaId(Long idComuna);

	// Administradores que de verdad pueden entrar: activos y que ya eligieron su contraseña (un pendiente no cuenta).
	long countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(Long idComuna, Rol rol);

	List<Usuario> findByComunaIdAndRol(Long idComuna, Rol rol);

	boolean existsByRol(Rol rol);

	boolean existsByComunaIdAndCorreo(Long idComuna, String correo);

	// Trae la comuna junto con el usuario: la sesión de Hibernate ya está cerrada cuando se usa.
	@Query("select u from Usuario u left join fetch u.comuna where u.id = :id")
	Optional<Usuario> findConComunaPorId(@Param("id") Long id);
}
