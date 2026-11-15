package com.micomuna.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.micomuna.entities.Usuario;
import com.micomuna.repositories.UsuarioRepository;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// No es un @Component a propósito: lo registra SecurityConfig dentro de la cadena de seguridad.
// Si no hay header Bearer deja pasar y decide SecurityConfig; si el token no sirve, responde 401.
public class JwtAuthFilter extends OncePerRequestFilter {

	private static final String PREFIJO = "Bearer ";

	private final JwtService jwt;
	private final UsuarioRepository usuarios;

	public JwtAuthFilter(JwtService jwt, UsuarioRepository usuarios) {
		this.jwt = jwt;
		this.usuarios = usuarios;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(PREFIJO)) {
			chain.doFilter(request, response);
			return;
		}

		UsuarioAutenticado autenticado = autenticar(header.substring(PREFIJO.length()).trim());
		if (autenticado == null) {
			SecurityContextHolder.clearContext();
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		var autoridad = new SimpleGrantedAuthority("ROLE_" + autenticado.rol().name());
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(autenticado, null, List.of(autoridad)));
		chain.doFilter(request, response);
	}

	// Devuelve null si el token no sirve o si el usuario o su comuna ya no están activos.
	private UsuarioAutenticado autenticar(String token) {
		DatosToken datos;
		try {
			datos = jwt.leer(token);
		} catch (JwtException e) {
			return null;
		}
		// El estado se lee de la base en cada petición, no del token.
		Usuario usuario = usuarios.findConComunaPorId(datos.usuarioId()).orElse(null);
		if (usuario == null || !usuario.isActivo()) {
			return null;
		}
		if (usuario.getComuna() != null && !usuario.getComuna().isActiva()) {
			return null;
		}
		Long comunaId = usuario.getComuna() == null ? null : usuario.getComuna().getId();
		return new UsuarioAutenticado(usuario.getId(), usuario.getRol(), comunaId);
	}
}
