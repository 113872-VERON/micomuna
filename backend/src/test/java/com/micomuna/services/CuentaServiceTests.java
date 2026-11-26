package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.micomuna.dtos.ActualizarMisDatosRequest;
import com.micomuna.dtos.CambiarClaveRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.CredencialesInvalidasException;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;
import com.micomuna.security.UsuarioAutenticado;

// Reglas de la búsqueda de "la persona de la sesión", compartida por Mi cuenta y el cambio de contraseña.
class CuentaServiceTests {

	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final CuentaService cuenta = new CuentaService(usuarios);
	private final AuthService auth = new AuthService(mock(ComunaService.class), usuarios, new BCryptPasswordEncoder(),
			new JwtService("un-secreto-de-prueba-bien-largo-de-mas-de-32-caracteres", Duration.ofHours(4)), cuenta);

	private Usuario deLaComuna(long id, long comunaId, boolean activo) {
		Comuna c = new Comuna();
		c.setId(comunaId);
		c.setSlug("comuna-" + comunaId);
		Usuario u = new Usuario();
		u.setId(id);
		u.setComuna(c);
		u.setCorreo("ana@mail.com");
		u.setNombre("Ana");
		u.setRol(Rol.VECINO);
		u.setActivo(activo);
		return u;
	}

	@Test
	void unaComunaDelTokenQueNoCoincideDa401EnLecturaCambioDeNombreYCambioDeClave() {
		// El usuario 10 es de la comuna 1, pero el token dice comuna 2: la búsqueda por id y comuna no lo encuentra.
		when(usuarios.findByIdAndComunaId(10L, 2L)).thenReturn(Optional.empty());
		UsuarioAutenticado desfasado = new UsuarioAutenticado(10L, Rol.VECINO, 2L);

		assertThatThrownBy(() -> cuenta.verMisDatos(desfasado)).isInstanceOf(CredencialesInvalidasException.class);
		assertThatThrownBy(() -> cuenta.cambiarNombre(desfasado, new ActualizarMisDatosRequest("Otro")))
				.isInstanceOf(CredencialesInvalidasException.class);
		assertThatThrownBy(() -> auth.cambiarClave(desfasado, new CambiarClaveRequest("clave-actual-1", "clave-nueva-22")))
				.isInstanceOf(CredencialesInvalidasException.class);
		verify(usuarios, never()).save(any());
	}

	@Test
	void unUsuarioInactivoDa401AunqueElTokenSeaDeSuComuna() {
		when(usuarios.findByIdAndComunaId(10L, 1L)).thenReturn(Optional.of(deLaComuna(10L, 1L, false)));
		UsuarioAutenticado sesion = new UsuarioAutenticado(10L, Rol.VECINO, 1L);

		assertThatThrownBy(() -> auth.cambiarClave(sesion, new CambiarClaveRequest("clave-actual-1", "clave-nueva-22")))
				.isInstanceOf(CredencialesInvalidasException.class);
		verify(usuarios, never()).save(any());
	}

	@Test
	void unTokenSinComunaSoloValeParaUnUsuarioSinComuna() {
		// Un token "de superadmin" (sin comuna) con el id de alguien que sí tiene comuna no sirve.
		when(usuarios.findById(10L)).thenReturn(Optional.of(deLaComuna(10L, 1L, true)));

		assertThatThrownBy(() -> cuenta.verMisDatos(new UsuarioAutenticado(10L, Rol.SUPERADMIN, null)))
				.isInstanceOf(CredencialesInvalidasException.class);
	}

	@Test
	void unUsuarioActivoDeLaComunaDelTokenSeEncuentra() {
		when(usuarios.findByIdAndComunaId(10L, 1L)).thenReturn(Optional.of(deLaComuna(10L, 1L, true)));

		assertThat(cuenta.verMisDatos(new UsuarioAutenticado(10L, Rol.VECINO, 1L)).comunaSlug()).isEqualTo("comuna-1");
	}
}
