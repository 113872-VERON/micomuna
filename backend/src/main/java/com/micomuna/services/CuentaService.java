package com.micomuna.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.dtos.ActualizarMisDatosRequest;
import com.micomuna.dtos.UsuarioDto;
import com.micomuna.entities.Usuario;
import com.micomuna.exceptions.CredencialesInvalidasException;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// Datos de la cuenta de quien tiene la sesión iniciada, sea cual sea su rol.
// El usuario sale siempre del token: ningún método recibe un id enviado por el cliente.
@Service
public class CuentaService {

	private final UsuarioRepository usuarios;

	public CuentaService(UsuarioRepository usuarios) {
		this.usuarios = usuarios;
	}

	@Transactional(readOnly = true)
	public UsuarioDto verMisDatos(UsuarioAutenticado autenticado) {
		return aDto(buscar(autenticado));
	}

	@Transactional
	public UsuarioDto cambiarNombre(UsuarioAutenticado autenticado, ActualizarMisDatosRequest pedido) {
		Usuario usuario = buscar(autenticado);
		usuario.setNombre(pedido.nombre());
		return aDto(usuarios.save(usuario));
	}

	// Búsqueda común de "la persona de la sesión" (también la usa AuthService al cambiar la clave).
	// Si el usuario ya no existe, está inactivo o no es de la comuna del token, la sesión no vale: 401.
	public Usuario buscar(UsuarioAutenticado autenticado) {
		// Los de comuna se buscan con la comuna del token; el superadmin (sin comuna) solo puede ser un usuario sin comuna.
		var encontrado = autenticado.comunaId() != null
				? usuarios.findByIdAndComunaId(autenticado.id(), autenticado.comunaId())
				: usuarios.findById(autenticado.id()).filter(u -> u.getComuna() == null);
		return encontrado.filter(Usuario::isActivo).orElseThrow(CredencialesInvalidasException::new);
	}

	private UsuarioDto aDto(Usuario usuario) {
		return UsuarioDto.desde(usuario, usuario.getComuna() == null ? null : usuario.getComuna().getSlug());
	}
}
