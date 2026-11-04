package com.micomuna.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;

import io.jsonwebtoken.JwtException;

class JwtServiceTests {

	private static final String SECRETO = "un-secreto-de-prueba-bien-largo-de-mas-de-32-caracteres";

	private Usuario usuario(Long id, Rol rol, Long idComuna) {
		Usuario u = new Usuario();
		u.setId(id);
		u.setRol(rol);
		if (idComuna != null) {
			Comuna c = new Comuna();
			c.setId(idComuna);
			u.setComuna(c);
		}
		return u;
	}

	@Test
	void generaYLeeUnTokenConSusDatos() {
		JwtService jwt = new JwtService(SECRETO, Duration.ofHours(4));

		DatosToken datos = jwt.leer(jwt.generar(usuario(7L, Rol.ADMINISTRATIVO, 3L)));

		assertThat(datos).isEqualTo(new DatosToken(7L, Rol.ADMINISTRATIVO, 3L));
	}

	@Test
	void elSuperadminNoTieneComuna() {
		JwtService jwt = new JwtService(SECRETO, Duration.ofHours(4));

		DatosToken datos = jwt.leer(jwt.generar(usuario(1L, Rol.SUPERADMIN, null)));

		assertThat(datos.comunaId()).isNull();
		assertThat(datos.rol()).isEqualTo(Rol.SUPERADMIN);
	}

	@Test
	void rechazaUnTokenManipulado() {
		JwtService jwt = new JwtService(SECRETO, Duration.ofHours(4));
		String token = jwt.generar(usuario(7L, Rol.VECINO, 3L));
		String manipulado = token.substring(0, token.length() - 4) + "AAAA";

		assertThatThrownBy(() -> jwt.leer(manipulado)).isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> jwt.leer("no-es-un-token")).isInstanceOf(JwtException.class);
	}

	@Test
	void rechazaUnTokenFirmadoConOtroSecreto() {
		JwtService otro = new JwtService("otro-secreto-distinto-tambien-de-mas-de-32-caracteres", Duration.ofHours(4));
		JwtService jwt = new JwtService(SECRETO, Duration.ofHours(4));

		assertThatThrownBy(() -> jwt.leer(otro.generar(usuario(7L, Rol.VECINO, 3L))))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void rechazaUnTokenVencido() {
		JwtService jwt = new JwtService(SECRETO, Duration.ofSeconds(-1));

		assertThatThrownBy(() -> jwt.leer(jwt.generar(usuario(7L, Rol.VECINO, 3L))))
				.isInstanceOf(JwtException.class);
	}

	@Test
	void fallaAlIniciarSiElSecretoEsMuyCorto() {
		assertThatThrownBy(() -> new JwtService("corto", Duration.ofHours(4)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new JwtService("", Duration.ofHours(4)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
