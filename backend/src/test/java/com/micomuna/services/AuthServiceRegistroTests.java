package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.micomuna.dtos.RegistroRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

class AuthServiceRegistroTests {

	@Test
	void dosRegistrosSimultaneosConElMismoCorreoDan409YNoUn500() {
		Comuna comuna = new Comuna();
		comuna.setId(1L);
		comuna.setSlug("salsipuedes-cordoba");
		ComunaService comunas = mock(ComunaService.class);
		when(comunas.buscarActivaPorSlug("salsipuedes-cordoba")).thenReturn(comuna);

		// La consulta previa no ve al otro registro, pero la restricción única de la base sí lo frena.
		UsuarioRepository usuarios = mock(UsuarioRepository.class);
		when(usuarios.existsByComunaIdAndCorreo(1L, "ana@mail.com")).thenReturn(false);
		when(usuarios.saveAndFlush(any(Usuario.class))).thenThrow(new DataIntegrityViolationException("ORA-00001"));

		AuthService servicio = new AuthService(comunas, usuarios, new BCryptPasswordEncoder(),
				new JwtService("un-secreto-de-prueba-bien-largo-de-mas-de-32-caracteres", Duration.ofHours(4)));

		assertThatThrownBy(() -> servicio.registrarVecino("salsipuedes-cordoba",
				new RegistroRequest("Ana", "ana@mail.com", "clave-segura-1")))
				.isInstanceOf(ConflictoException.class)
				.hasMessage("Ese correo ya está registrado en esta comuna");
	}
}
