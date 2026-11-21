package com.micomuna.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Service;

import com.micomuna.entities.CodigoRecuperacion;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.TipoCodigo;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.repositories.CodigoRecuperacionRepository;

import lombok.RequiredArgsConstructor;

// Códigos de un solo uso que viajan por correo (recuperar la clave o aceptar una invitación).
// En la base solo queda su SHA-256: quien lea la tabla no puede armar un enlace válido.
// Los métodos se llaman dentro de la transacción del servicio que los usa.
@Service
@RequiredArgsConstructor
public class CodigoService {

	static final Duration VIGENCIA_RECUPERACION = Duration.ofMinutes(60);
	static final Duration VIGENCIA_INVITACION = Duration.ofHours(72);

	private static final SecureRandom ALEATORIO = new SecureRandom();

	private final CodigoRecuperacionRepository codigos;

	// Devuelve el código en claro (solo para ponerlo en el correo) e invalida los anteriores del mismo tipo.
	public String emitir(Usuario usuario, TipoCodigo tipo) {
		invalidar(usuario, tipo);

		byte[] bytes = new byte[32];
		ALEATORIO.nextBytes(bytes);
		String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

		CodigoRecuperacion nuevo = new CodigoRecuperacion();
		nuevo.setUsuario(usuario);
		nuevo.setCodigoHash(hash(codigo));
		nuevo.setTipo(tipo);
		nuevo.setVenceEn(Instant.now().plus(tipo == TipoCodigo.INVITACION ? VIGENCIA_INVITACION : VIGENCIA_RECUPERACION));
		codigos.saveAndFlush(nuevo);
		return codigo;
	}

	// Deja sin efecto los códigos vigentes del usuario de ese tipo (por ejemplo, la invitación que se mandó a un
	// correo que después se corrigió). Con tipo nulo, los de cualquier tipo.
	public void invalidar(Usuario usuario, TipoCodigo tipo) {
		for (CodigoRecuperacion previo : codigos.findPendientesDelUsuario(usuario.getId())) {
			if (tipo == null || previo.getTipo() == tipo) {
				previo.setUsado(true);
			}
		}
	}

	// Gasta el código y devuelve a su dueño. Inexistente, de otro tipo, usado, vencido, de otra comuna
	// o de un usuario inactivo: siempre el mismo error.
	public Usuario canjear(Long comunaId, String codigo, TipoCodigo tipo) {
		CodigoRecuperacion encontrado = codigos.findByCodigoHash(hash(codigo)).orElse(null);
		if (encontrado == null || encontrado.isUsado() || encontrado.getTipo() != tipo
				|| !encontrado.getVenceEn().isAfter(Instant.now())) {
			throw invalido();
		}
		Usuario usuario = encontrado.getUsuario();
		if (usuario.getComuna() == null || !usuario.getComuna().getId().equals(comunaId) || !usuario.isActivo()) {
			throw invalido();
		}

		// Una vez usado, no quedan otros códigos sueltos de esa persona.
		for (CodigoRecuperacion pendiente : codigos.findPendientesDelUsuario(usuario.getId())) {
			pendiente.setUsado(true);
		}
		encontrado.setUsado(true);
		codigos.flush();
		return usuario;
	}

	private static PeticionInvalidaException invalido() {
		return new PeticionInvalidaException("CODIGO_INVALIDO", "El enlace no es válido o ya venció. Pedí uno nuevo");
	}

	private static String hash(String codigo) {
		try {
			byte[] resumen = MessageDigest.getInstance("SHA-256").digest(codigo.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(resumen);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
