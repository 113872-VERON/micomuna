package com.micomuna.services;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.dtos.AuthRespuesta;
import com.micomuna.dtos.RegistroRequest;
import com.micomuna.dtos.UsuarioDto;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

	private static final String CORREO_REPETIDO = "Ese correo ya está registrado en esta comuna";

	private final ComunaService comunaService;
	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final JwtService jwt;

	@Transactional
	public AuthRespuesta registrarVecino(String slug, RegistroRequest pedido) {
		Comuna comuna = comunaService.buscarActivaPorSlug(slug);
		if (usuarios.existsByComunaIdAndCorreo(comuna.getId(), pedido.correo())) {
			throw new ConflictoException(CORREO_REPETIDO);
		}

		Usuario usuario = new Usuario();
		usuario.setComuna(comuna);
		usuario.setCorreo(pedido.correo());
		usuario.setNombre(pedido.nombre());
		usuario.setClaveHash(encoder.encode(pedido.clave()));
		usuario.setRol(Rol.VECINO);
		usuario.setActivo(true);
		try {
			usuarios.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Otro registro con el mismo correo entró entre la consulta y el guardado.
			throw new ConflictoException(CORREO_REPETIDO);
		}

		return new AuthRespuesta(jwt.generar(usuario), UsuarioDto.desde(usuario, comuna.getSlug()));
	}
}
