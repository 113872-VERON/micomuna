package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.DecisionSugerenciaIa;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.MismoTipoException;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.ReclamoCerradoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.SugerenciaIaRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;

// MC-25: las reglas de CambioDeTipoService, sin base de datos: repositorios simulados. (El endpoint completo y la base real
// están en CambioDeTipoTests; la carrera con la IA, en CambioDeTipoConcurrenciaTests.)
class CambioDeTipoServiceTests {

	private final ReclamoRepository reclamos = mock(ReclamoRepository.class);
	private final ReclamoEstadoRepository historial = mock(ReclamoEstadoRepository.class);
	private final SugerenciaIaRepository sugerencias = mock(SugerenciaIaRepository.class);
	private final TipoReclamoRepository tipos = mock(TipoReclamoRepository.class);
	private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
	private final ComunaActual comunaActual = mock(ComunaActual.class);
	private final Instant ahora = Instant.parse("2026-11-25T12:00:00Z");
	private final CambioDeTipoService servicio = new CambioDeTipoService(reclamos, historial, sugerencias, tipos, usuarios,
			comunaActual, Clock.fixed(ahora, ZoneOffset.UTC));

	private final Reclamo reclamo = new Reclamo();
	private final Usuario quien = new Usuario();

	private TipoReclamo tipo(String codigo, String nombre) {
		TipoReclamo t = mock(TipoReclamo.class);
		when(t.getCodigo()).thenReturn(codigo);
		when(t.getNombre()).thenReturn(nombre);
		when(tipos.findByCodigo(codigo)).thenReturn(Optional.of(t));
		return t;
	}

	@BeforeEach
	void preparar() {
		when(comunaActual.id()).thenReturn(5L);
		reclamo.setId(70L);
		reclamo.setNumero(12);
		reclamo.setEstado(EstadoReclamo.ASIGNADO);
		reclamo.setTipo(tipo("BACHE", "Bache"));
		tipo("LUZ", "Luz");
		when(reclamos.bloquearPorNumero(5L, 12)).thenReturn(Optional.of(reclamo));
		quien.setId(9L);
		quien.setRol(Rol.ADMINISTRATIVO);
		when(usuarios.findByIdAndComunaId(9L, 5L)).thenReturn(Optional.of(quien));
	}

	private SugerenciaIa hecha(String tipo) {
		return SugerenciaIa.hecha(5L, 70L, ProveedorIa.CLAUDE, "m", tipo, PrioridadIa.BAJA, "porque sí", 1L, 1L);
	}

	@Test
	void aceptarLaSugerenciaRegistraLaDecisionConElUsuarioYLaHora() {
		SugerenciaIa s = hecha("LUZ");
		when(sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(70L)).thenReturn(Optional.of(s));

		servicio.cambiarTipo(12, "LUZ", 9L);

		assertThat(s.getDecision()).isEqualTo(DecisionSugerenciaIa.ACEPTADA);
		assertThat(s.getTipoDecidido()).isEqualTo("LUZ");
		assertThat(s.getIdUsuarioDecision()).isEqualTo(9L);
		assertThat(s.getDecididaEn()).isEqualTo(ahora);
		assertThat(reclamo.getTipo().getCodigo()).isEqualTo("LUZ");
	}

	@Test
	void unTipoDistintoDelSugeridoEsUnaCorreccionYDejaFilaDeHistorialConElMismoEstado() {
		SugerenciaIa s = hecha("LUZ");
		tipo("AGUA", "Agua");
		when(sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(70L)).thenReturn(Optional.of(s));

		servicio.cambiarTipo(12, "AGUA", 9L);

		assertThat(s.getDecision()).isEqualTo(DecisionSugerenciaIa.CORREGIDA);
		ArgumentCaptor<ReclamoEstado> fila = ArgumentCaptor.forClass(ReclamoEstado.class);
		verify(historial).save(fila.capture());
		assertThat(fila.getValue().getEstadoAnterior()).isEqualTo(EstadoReclamo.ASIGNADO);
		assertThat(fila.getValue().getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
		assertThat(fila.getValue().getObservacion()).isEqualTo("Cambió el tipo de Bache a Agua");
		assertThat(fila.getValue().getUsuario()).isSameAs(quien);
		assertThat(reclamo.getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
	}

	@Test
	void unaSugerenciaConErrorNoSeDecideYElTipoCambiaIgual() {
		SugerenciaIa s = SugerenciaIa.conError(5L, 70L, ProveedorIa.CLAUDE, "m", "falló", null, null);
		when(sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(70L)).thenReturn(Optional.of(s));

		servicio.cambiarTipo(12, "LUZ", 9L);

		assertThat(s.getDecision()).isNull();
		assertThat(reclamo.getTipo().getCodigo()).isEqualTo("LUZ");
	}

	@Test
	void elTipoActualSoloDecideSiHayUnaSugerenciaHechaSinDecidir() {
		SugerenciaIa s = hecha("LUZ");
		when(sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(70L)).thenReturn(Optional.of(s));

		servicio.cambiarTipo(12, "BACHE", 9L);

		assertThat(s.getDecision()).isEqualTo(DecisionSugerenciaIa.CORREGIDA);
		verify(historial, never()).save(any());
		assertThatThrownBy(() -> servicio.cambiarTipo(12, "BACHE", 9L)).isInstanceOf(MismoTipoException.class);
	}

	@Test
	void elMismoTipoSinSugerenciaDaMismoTipo() {
		when(sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(anyLong())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.cambiarTipo(12, "BACHE", 9L)).isInstanceOf(MismoTipoException.class);
	}

	@Test
	void cerradoTipoInvalidoYReclamoInexistente() {
		reclamo.setEstado(EstadoReclamo.RESUELTO);
		assertThatThrownBy(() -> servicio.cambiarTipo(12, "LUZ", 9L)).isInstanceOf(ReclamoCerradoException.class);
		reclamo.setEstado(EstadoReclamo.DESCARTADO);
		assertThatThrownBy(() -> servicio.cambiarTipo(12, "LUZ", 9L)).isInstanceOf(ReclamoCerradoException.class);

		reclamo.setEstado(EstadoReclamo.RECIBIDO);
		when(tipos.findByCodigo("XX")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> servicio.cambiarTipo(12, "XX", 9L)).isInstanceOf(PeticionInvalidaException.class)
				.extracting("codigo").isEqualTo("TIPO_INVALIDO");

		assertThatThrownBy(() -> servicio.cambiarTipo(99, "LUZ", 9L)).isInstanceOf(RecursoNoEncontradoException.class);
	}

	@Test
	void soloElPersonalAdministrativoDecide() {
		for (Rol rol : new Rol[] { Rol.VECINO, Rol.AGENTE, Rol.SUPERADMIN }) {
			quien.setRol(rol);
			assertThatThrownBy(() -> servicio.cambiarTipo(12, "LUZ", 9L)).isInstanceOf(AccessDeniedException.class);
		}
		quien.setRol(Rol.ADMIN_COMUNA);
		servicio.cambiarTipo(12, "LUZ", 9L);
		assertThat(reclamo.getTipo().getCodigo()).isEqualTo("LUZ");
	}
}
