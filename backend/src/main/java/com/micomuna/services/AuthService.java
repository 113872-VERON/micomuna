package com.micomuna.services;

import java.nio.charset.StandardCharsets;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.dtos.AuthRespuesta;
import com.micomuna.dtos.LoginRequest;
import com.micomuna.dtos.RegistroRequest;
import com.micomuna.dtos.UsuarioDto;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.CredencialesInvalidasException;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

@Service
public class AuthService {

	private static final String CORREO_REPETIDO = "Ese correo ya está registrado en esta comuna";
	// BCrypt solo mira los primeros 72 bytes; una clave más larga nunca pudo haberse registrado.
	private static final int MAXIMO_BYTES_CLAVE = 72;

	private final ComunaService comunaService;
	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final JwtService jwt;
	// Se compara contra este hash cuando el usuario no existe, para que tarde lo mismo que con uno real.
	private final String hashDeRelleno;

	public AuthService(ComunaService comunaService, UsuarioRepository usuarios, PasswordEncoder encoder,
			JwtService jwt) {
		this.comunaService = comunaService;
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.jwt = jwt;
		this.hashDeRelleno = encoder.encode("hash-de-relleno-sin-uso");
	}

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

	// Cualquier falla (correo inexistente, clave mala, usuario inactivo o pendiente, usuario de otra comuna
	// o superadmin) lanza el mismo error y tarda parecido.
	@Transactional(readOnly = true)
	public AuthRespuesta login(String slug, LoginRequest pedido) {
		Comuna comuna = comunaService.buscarActivaPorSlug(slug);
		Usuario usuario = usuarios.findByComunaIdAndCorreo(comuna.getId(), pedido.correo()).orElse(null);

		String hash = usuario == null || usuario.getClaveHash() == null ? hashDeRelleno : usuario.getClaveHash();
		boolean claveCorrecta = claveEntraEnBcrypt(pedido.clave()) && encoder.matches(pedido.clave(), hash);
		if (usuario == null || usuario.getClaveHash() == null || !claveCorrecta || !usuario.isActivo()) {
			throw new CredencialesInvalidasException();
		}

		return new AuthRespuesta(jwt.generar(usuario), UsuarioDto.desde(usuario, comuna.getSlug()));
	}

	private boolean claveEntraEnBcrypt(String clave) {
		return clave.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_CLAVE;
	}
}
