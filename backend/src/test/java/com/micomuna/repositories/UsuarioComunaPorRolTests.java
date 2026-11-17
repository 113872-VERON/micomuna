package com.micomuna.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;

// Criterio 1 de MC-13: el superadmin no pertenece a ninguna comuna y todos los demás roles sí.
// La regla vive en la entidad (no en una migración): ningún camino de código puede guardar otra combinación.
@SpringBootTest
@Transactional
class UsuarioComunaPorRolTests {

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	private Comuna comuna;

	@BeforeEach
	void preparar() {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comuna = new Comuna();
		comuna.setNombre("Comuna de prueba");
		comuna.setProvincia(Provincia.CORDOBA);
		comuna.setSlug("t13-rol-comuna");
		comunas.saveAndFlush(comuna);
	}

	private Usuario usuario(Rol rol, Comuna deLaComuna) {
		Usuario u = new Usuario();
		u.setComuna(deLaComuna);
		u.setCorreo(rol.name().toLowerCase() + "@mail.com");
		u.setClaveHash("hash");
		u.setNombre("Nombre");
		u.setRol(rol);
		return u;
	}

	@Test
	void todosLosRolesQueNoSonSuperadminSeGuardanConComuna() {
		for (Rol rol : Rol.values()) {
			if (rol != Rol.SUPERADMIN) {
				assertThat(usuarios.saveAndFlush(usuario(rol, comuna)).getId()).isNotNull();
			}
		}
	}

	@Test
	void elSuperadminSeGuardaSinComuna() {
		assertThat(usuarios.saveAndFlush(usuario(Rol.SUPERADMIN, null)).getComuna()).isNull();
	}

	@Test
	void elSuperadminNoPuedeTenerComuna() {
		assertThatThrownBy(() -> usuarios.saveAndFlush(usuario(Rol.SUPERADMIN, comuna)))
				.hasStackTraceContaining("superadmin");
	}

	@Test
	void losDemasRolesNoPuedenQuedarSinComuna() {
		for (Rol rol : Rol.values()) {
			if (rol != Rol.SUPERADMIN) {
				assertThatThrownBy(() -> usuarios.saveAndFlush(usuario(rol, null)))
						.as("rol " + rol)
						.hasStackTraceContaining("comuna");
			}
		}
	}

	@Test
	void unCambioDeRolTampocoPuedeRomperLaRegla() {
		Usuario vecino = usuarios.saveAndFlush(usuario(Rol.VECINO, comuna));
		vecino.setRol(Rol.SUPERADMIN);

		assertThatThrownBy(() -> usuarios.saveAndFlush(vecino)).hasStackTraceContaining("superadmin");
	}
}
