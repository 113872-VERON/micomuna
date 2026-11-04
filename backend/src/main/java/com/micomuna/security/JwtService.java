package com.micomuna.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private static final int SECRETO_MINIMO = 32;

	private final SecretKey clave;
	private final Duration expiracion;

	public JwtService(@Value("${jwt.secret:}") String secreto, @Value("${jwt.expiracion:4h}") Duration expiracion) {
		if (secreto == null || secreto.length() < SECRETO_MINIMO) {
			throw new IllegalArgumentException(
					"JWT_SECRET falta o es muy corto: necesita al menos " + SECRETO_MINIMO + " caracteres");
		}
		this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
		this.expiracion = expiracion;
	}

	public String generar(Usuario usuario) {
		Date ahora = new Date();
		Long comunaId = usuario.getComuna() == null ? null : usuario.getComuna().getId();
		return Jwts.builder()
				.subject(String.valueOf(usuario.getId()))
				.claim("rol", usuario.getRol().name())
				.claim("comuna", comunaId)
				.issuedAt(ahora)
				.expiration(new Date(ahora.getTime() + expiracion.toMillis()))
				.signWith(clave)
				.compact();
	}

	// Lanza JwtException si el token está manipulado, vencido o mal formado.
	public DatosToken leer(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
			Long comunaId = claims.get("comuna", Long.class);
			return new DatosToken(Long.valueOf(claims.getSubject()), Rol.valueOf(claims.get("rol", String.class)),
					comunaId);
		} catch (IllegalArgumentException e) {
			// Datos del token con formato inesperado: se trata como un token inválido.
			throw new JwtException("Token inválido", e);
		}
	}
}
