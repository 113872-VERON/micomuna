package com.micomuna.configs;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.micomuna.dtos.ClaveValidaValidator;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.UsuarioRepository;

/**
 * Crea al iniciar el único superadmin a partir de SUPERADMIN_CORREO y SUPERADMIN_CLAVE, solo si todavía no existe
 * ninguno. Las variables sirven para crearlo: cambiarlas después no modifica al superadmin que ya existe.
 * Si olvida su clave no hay recuperación por correo: se borra su fila de la tabla USUARIO y se reinicia con
 * variables nuevas. Nunca se escribe la clave en el log.
 */
@Component
public class SuperadminInicializador implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SuperadminInicializador.class);

	private final UsuarioRepository usuarios;
	private final PasswordEncoder encoder;
	private final String correo;
	private final String clave;
	private final int claveMinima;

	public SuperadminInicializador(UsuarioRepository usuarios, PasswordEncoder encoder,
			@Value("${superadmin.correo:}") String correo, @Value("${superadmin.clave:}") String clave,
			@Value("${superadmin.clave-minima:8}") int claveMinima) {
		this.usuarios = usuarios;
		this.encoder = encoder;
		this.correo = correo;
		this.clave = clave;
		this.claveMinima = claveMinima;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (usuarios.existsByRol(Rol.SUPERADMIN)) {
			return;
		}
		if (correo == null || correo.isBlank() || clave == null || clave.isBlank()) {
			log.warn("No hay superadmin y faltan SUPERADMIN_CORREO y SUPERADMIN_CLAVE: no se crea ninguno. "
					+ "Sin superadmin no se pueden dar de alta comunas.");
			return;
		}

		String correoNormalizado = correo.trim().toLowerCase(Locale.ROOT);
		if (!correoNormalizado.matches("[^@\\s]+@[^@\\s]+")) {
			log.error("SUPERADMIN_CORREO no es un correo válido: no se crea el superadmin.");
			return;
		}
		String motivo = ClaveValidaValidator.motivoDeRechazo(clave);
		if (motivo == null && clave.length() < claveMinima) {
			motivo = "tiene que tener al menos " + claveMinima + " caracteres";
		}
		if (motivo != null) {
			log.error("SUPERADMIN_CLAVE no sirve ({}): no se crea el superadmin.", motivo);
			return;
		}

		Usuario superadmin = new Usuario();
		superadmin.setCorreo(correoNormalizado);
		superadmin.setNombre("Superadministrador");
		superadmin.setClaveHash(encoder.encode(clave));
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setActivo(true);
		try {
			usuarios.saveAndFlush(superadmin);
			log.info("Superadmin creado: {}", correoNormalizado);
		} catch (DataIntegrityViolationException e) {
			// Otra instancia lo creó al mismo tiempo: el índice único deja uno solo.
			log.info("El superadmin ya existe: no se crea otro.");
		}
	}
}
