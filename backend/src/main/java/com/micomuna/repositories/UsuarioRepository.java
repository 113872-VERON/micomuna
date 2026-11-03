package com.micomuna.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

	Optional<Usuario> findByComunaIdAndCorreo(Long idComuna, String correo);

	Optional<Usuario> findByComunaIsNullAndCorreo(String correo);

	boolean existsByRol(Rol rol);
}
