package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.micomuna.dtos.LoginRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.CredencialesInvalidasException;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

class AuthServiceLoginTests {

	@Test
	void unUsuarioPendienteDeInvitacionSinClaveNoPuedeEntrar() {
		Comuna comuna = new Comuna();
		comuna.setId(1L);
		comuna.setSlug("salsipuedes-cordoba");
		ComunaService comunas = mock(ComunaService.class);
		when(comunas.buscarActivaPorSlug("salsipuedes-cordoba")).thenReturn(comuna);

		// Todavía no eligió su contraseña: la invitación está pendiente y clave_hash está vacío.
		Usuario pendiente = new Usuario();
		pendiente.setId(5L);
		pendiente.setComuna(comuna);
		pendiente.setCorreo("ana@mail.com");
		pendiente.setRol(Rol.AGENTE);
		pendiente.setActivo(true);
		pendiente.setClaveHash(null);
		UsuarioRepository usuarios = mock(UsuarioRepository.class);
		when(usuarios.findByComunaIdAndCorreo(1L, "ana@mail.com")).thenReturn(Optional.of(pendiente));

		AuthService servicio = new AuthService(comunas, usuarios, new BCryptPasswordEncoder(),
				new JwtService("un-secreto-de-prueba-bien-largo-de-mas-de-32-caracteres", Duration.ofHours(4)));

		assertThatThrownBy(() -> servicio.login("salsipuedes-cordoba", new LoginRequest("ana@mail.com", "cualquiera-123")))
				.isInstanceOf(CredencialesInvalidasException.class)
				.hasMessage("Credenciales incorrectas");
	}
}
