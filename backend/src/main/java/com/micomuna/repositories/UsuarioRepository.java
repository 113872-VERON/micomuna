package com.micomuna.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByComunaIdAndCorreo(Long idComuna, String correo);

	Optional<Usuario> findByComunaIsNullAndCorreo(String correo);

	// Patrón para todo lo que es de una comuna: se busca siempre por id Y por la comuna del token (ComunaActual).
	Optional<Usuario> findByIdAndComunaId(Long id, Long idComuna);

	List<Usuario> findByComunaIdAndRol(Long idComuna, Rol rol);

	boolean existsByRol(Rol rol);

	boolean existsByComunaIdAndCorreo(Long idComuna, String correo);

	// Trae la comuna junto con el usuario: la sesión de Hibernate ya está cerrada cuando se usa.
	@Query("select u from Usuario u left join fetch u.comuna where u.id = :id")
	Optional<Usuario> findConComunaPorId(@Param("id") Long id);
}
