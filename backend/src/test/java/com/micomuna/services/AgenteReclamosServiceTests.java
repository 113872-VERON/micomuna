package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.micomuna.dtos.ReclamoAgenteDetalleDto;
import com.micomuna.dtos.ResolverReclamoForm;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.Rol;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

// MC-20: la respuesta de empezar y resolver sale del mismo cambio, no de una lectura posterior filtrada por agente.
// Se simula que el personal reasignó el reclamo justo después del cambio: la búsqueda "como agente" ya no lo encuentra.
class AgenteReclamosServiceTests {

	private static final UsuarioAutenticado AGENTE = new UsuarioAutenticado(7L, Rol.AGENTE, 1L);

	private ReclamoRepository reclamos;
	private ReclamoFotoRepository fotos;
	private CambioDeEstadoService cambios;
	private AgenteReclamosService servicio;
	private Reclamo reclamo;

	@BeforeEach
	void preparar() {
		reclamos = mock(ReclamoRepository.class);
		fotos = mock(ReclamoFotoRepository.class);
		cambios = mock(CambioDeEstadoService.class);
		ComunaActual comunaActual = mock(ComunaActual.class);
		when(comunaActual.id()).thenReturn(1L);
		PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
		when(transacciones.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

		TipoReclamo tipo = mock(TipoReclamo.class);
		when(tipo.getCodigo()).thenReturn("BACHE");
		when(tipo.getNombre()).thenReturn("Bache");
		reclamo = mock(Reclamo.class);
		when(reclamo.getId()).thenReturn(50L);
		when(reclamo.getNumero()).thenReturn(1);
		when(reclamo.getTipo()).thenReturn(tipo);
		when(reclamo.getDescripcion()).thenReturn("Un pozo");
		when(reclamo.getLatitud()).thenReturn(new BigDecimal("-31.420083"));
		when(reclamo.getLongitud()).thenReturn(new BigDecimal("-64.188776"));
		when(reclamo.getCreadoEn()).thenReturn(Instant.now());
		when(fotos.datosDe(50L)).thenReturn(List.of());
		servicio = new AgenteReclamosService(reclamos, fotos, cambios, comunaActual, mock(TurnosDeFotos.class), transacciones);
	}

	@Test
	void empezarResponde200ConElResultadoDeSuCambioAunqueElPersonalLoReasigneDespues() {
		when(reclamo.getEstado()).thenReturn(EstadoReclamo.EN_REPARACION);
		when(cambios.cambiar(eq(1), any(CambioDeEstado.class), eq(7L), eq(7L), eq(EstadoReclamo.ASIGNADO))).thenReturn(reclamo);
		// Después del cambio, "como agente" ya no lo encuentra (se lo reasignaron a otro).
		when(reclamos.buscarDelAgente(anyLong(), anyInt(), anyLong())).thenReturn(Optional.empty());

		ReclamoAgenteDetalleDto respuesta = servicio.empezar(1, AGENTE);

		assertThat(respuesta.numero()).isEqualTo(1);
		assertThat(respuesta.estado()).isEqualTo(EstadoReclamo.EN_REPARACION);
	}

	@Test
	void resolverResponde200ConElResultadoDeSuCambioAunqueElPersonalLoReasigneDespues() {
		Reclamo previo = mock(Reclamo.class);
		when(previo.getEstado()).thenReturn(EstadoReclamo.EN_REPARACION);
		// La revisión previa lo encuentra; cualquier búsqueda posterior no.
		when(reclamos.buscarDelAgente(1L, 1, 7L)).thenReturn(Optional.of(previo)).thenReturn(Optional.empty());
		when(reclamo.getEstado()).thenReturn(EstadoReclamo.RESUELTO);
		when(reclamo.getTrabajoRealizado()).thenReturn("Listo");
		when(cambios.cambiar(eq(1), any(CambioDeEstado.class), eq(7L), eq(7L), eq(EstadoReclamo.EN_REPARACION)))
				.thenReturn(reclamo);

		ReclamoAgenteDetalleDto respuesta = servicio.resolver(1, new ResolverReclamoForm("Listo", null), AGENTE);

		assertThat(respuesta.estado()).isEqualTo(EstadoReclamo.RESUELTO);
		assertThat(respuesta.trabajoRealizado()).isEqualTo("Listo");
	}

	@Test
	void laNotaDelHistorialSeCortaA500ConPuntosSuspensivos() {
		assertThat(AgenteReclamosService.notaParaElHistorial(null)).isNull();
		assertThat(AgenteReclamosService.notaParaElHistorial("   ")).isNull();
		assertThat(AgenteReclamosService.notaParaElHistorial(" hola ")).isEqualTo("hola");
		assertThat(AgenteReclamosService.notaParaElHistorial("a".repeat(500))).hasSize(500).doesNotEndWith("…");
		assertThat(AgenteReclamosService.notaParaElHistorial("a".repeat(501))).hasSize(500).endsWith("…");
	}
}
