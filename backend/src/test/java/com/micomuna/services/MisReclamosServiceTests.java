package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.micomuna.entities.ReclamoFoto;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

// La base ya solo admite fotos image/jpeg o image/png (ck_reclamo_foto_tipo), así que el servicio no se puede probar
// con una foto de otro tipo contra la base: acá el repositorio es simulado. Es la segunda barrera.
class MisReclamosServiceTests {

	private static final UsuarioAutenticado VECINO = new UsuarioAutenticado(5L, Rol.VECINO, 2L);

	private ReclamoFotoRepository fotos;
	private MisReclamosService servicio;

	@BeforeEach
	void preparar() {
		fotos = mock(ReclamoFotoRepository.class);
		ComunaActual comunaActual = mock(ComunaActual.class);
		when(comunaActual.id()).thenReturn(2L);
		servicio = new MisReclamosService(mock(ReclamoRepository.class), fotos, mock(ReclamoEstadoRepository.class),
				comunaActual);
	}

	private void guardada(String tipo) {
		ReclamoFoto foto = new ReclamoFoto(null, OrigenFoto.VECINO, 1, new byte[] { 1, 2, 3 }, tipo);
		when(fotos.buscarDelVecino(any(), any(), any(), any())).thenReturn(Optional.of(foto));
	}

	@Test
	void sirveLasFotosJpegYPng() {
		guardada("image/jpeg");
		assertThat(servicio.foto(1, 1L, VECINO).tipo()).isEqualTo("image/jpeg");
		guardada("image/png");
		assertThat(servicio.foto(1, 1L, VECINO).tipo()).isEqualTo("image/png");
	}

	@Test
	void unTipoQueNoEsJpegNiPngNoSeSirveAunqueEsteGuardado() {
		for (String tipo : new String[] { "text/html", "image/svg+xml", "image/gif", "application/octet-stream" }) {
			guardada(tipo);
			assertThatThrownBy(() -> servicio.foto(1, 1L, VECINO)).as(tipo).isInstanceOf(RecursoNoEncontradoException.class);
		}
	}

	@Test
	void sinFotoParaEseVecinoYComunaEsNoEncontrado() {
		when(fotos.buscarDelVecino(any(), any(), any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.foto(1, 1L, VECINO)).isInstanceOf(RecursoNoEncontradoException.class);
	}
}
