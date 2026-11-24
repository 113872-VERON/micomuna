package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

import com.micomuna.client.AsistenteIa;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.enums.EstadoSugerenciaIa;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.models.PedidoIa;
import com.micomuna.models.ReclamoRegistrado;
import com.micomuna.models.ResultadoIa;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.SugerenciaIaRepository;
import com.micomuna.repositories.TipoReclamoRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// MC-24: las reglas del análisis de la IA, sin base de datos ni red: repositorios y ejecutor simulados, y un asistente
// falso que se configura en cada prueba. (El alta de verdad, con commit y otro hilo, está en AltaConAnalisisIaTests.)
class AnalisisIaServiceTests {

	private static final String DESCRIPCION_SECRETA = "Vivo en el 742 de Evergreen Terrace y mi tel es 351-555-0100";

	static class AsistenteFalso implements AsistenteIa {
		boolean disponible = true;
		Function<PedidoIa, ResultadoIa> respuesta = p -> new ResultadoIa.Hecha("BACHE", PrioridadIa.ALTA, "Pozo profundo.", 100L, 20L);
		final java.util.List<PedidoIa> pedidos = new java.util.ArrayList<>();

		@Override
		public ProveedorIa proveedor() {
			return ProveedorIa.CLAUDE;
		}

		@Override
		public String modelo() {
			return "claude-haiku-5-5";
		}

		@Override
		public boolean disponible() {
			return disponible;
		}

		@Override
		public ResultadoIa analizar(PedidoIa pedido) {
			pedidos.add(pedido);
			return respuesta.apply(pedido);
		}
	}

	private final AsistenteFalso asistente = new AsistenteFalso();
	private final SugerenciaIaRepository sugerencias = mock(SugerenciaIaRepository.class);
	private final ReclamoFotoRepository fotos = mock(ReclamoFotoRepository.class);
	private final TipoReclamoRepository tipos = mock(TipoReclamoRepository.class);
	private final PlatformTransactionManager transacciones = mock(PlatformTransactionManager.class);
	// 23/11/2026 02:30 UTC = 22/11/2026 23:30 en Argentina: el "día argentino" arranca el 22 a las 03:00 UTC.
	private final Clock reloj = Clock.fixed(Instant.parse("2026-11-23T02:30:00Z"), ZoneOffset.UTC);
	private final ReclamoRegistrado reclamo = new ReclamoRegistrado(7L, "salsipuedes", 70L, 12, "BACHE", DESCRIPCION_SECRETA);

	private Executor ejecutor = Runnable::run;
	private Executor ejecutorDeOmisiones = Runnable::run;
	private ListAppender<ILoggingEvent> registro;
	private Logger logger;

	@BeforeEach
	void preparar() {
		TipoReclamo bache = mock(TipoReclamo.class);
		when(bache.getCodigo()).thenReturn("BACHE");
		when(bache.getNombre()).thenReturn("Bache o calle rota");
		TipoReclamo luz = mock(TipoReclamo.class);
		when(luz.getCodigo()).thenReturn("LUZ");
		when(luz.getNombre()).thenReturn("Luminaria apagada");
		when(tipos.findAllByOrderByOrdenAsc()).thenReturn(List.of(bache, luz));
		when(fotos.delVecinoDe(70L)).thenReturn(List.of(
				new ReclamoFoto(null, OrigenFoto.VECINO, 1, new byte[] { 1, 2, 3 }, "image/jpeg")));
		when(sugerencias.contarAnalisisDesde(anyLong(), any())).thenReturn(0L);
		logger = (Logger) LoggerFactory.getLogger(AnalisisIaService.class);
		registro = new ListAppender<>();
		registro.start();
		logger.addAppender(registro);
	}

	@AfterEach
	void dejarDeEscuchar() {
		logger.detachAppender(registro);
	}

	private AnalisisIaService servicio(int tope) {
		return servicio(tope, 1000);
	}

	private AnalisisIaService servicio(int tope, int topeTotal) {
		return new AnalisisIaService(asistente, ejecutor, ejecutorDeOmisiones, sugerencias, fotos, tipos, transacciones, reloj,
				String.valueOf(tope), String.valueOf(topeTotal));
	}

	private SugerenciaIa guardada() {
		ArgumentCaptor<SugerenciaIa> captor = ArgumentCaptor.forClass(SugerenciaIa.class);
		verify(sugerencias).save(captor.capture());
		return captor.getValue();
	}

	// ---- HECHA

	@Test
	void unaRespuestaValidaSeGuardaComoHechaConProveedorModeloYTokens() {
		servicio(200).alCrearElReclamo(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.HECHA);
		assertThat(fila.getIdComuna()).isEqualTo(7L);
		assertThat(fila.getIdReclamo()).isEqualTo(70L);
		assertThat(fila.getTipoSugerido()).isEqualTo("BACHE");
		assertThat(fila.getPrioridadSugerida()).isEqualTo(PrioridadIa.ALTA);
		assertThat(fila.getJustificacion()).isEqualTo("Pozo profundo.");
		assertThat(fila.getProveedor()).isEqualTo(ProveedorIa.CLAUDE);
		assertThat(fila.getModelo()).isEqualTo("claude-haiku-5-5");
		assertThat(fila.getTokensEntrada()).isEqualTo(100L);
		assertThat(fila.getTokensSalida()).isEqualTo(20L);
		assertThat(fila.getDetalleError()).isNull();
	}

	@Test
	void alProveedorSeLeMandaSoloElTipoLaDescripcionLasFotosYElCatalogo() {
		servicio(200).analizar(reclamo);

		assertThat(asistente.pedidos).hasSize(1);
		PedidoIa pedido = asistente.pedidos.get(0);
		assertThat(pedido.tipoElegido()).isEqualTo("BACHE");
		assertThat(pedido.descripcion()).isEqualTo(DESCRIPCION_SECRETA);
		assertThat(pedido.fotos()).hasSize(1);
		assertThat(pedido.fotos().get(0).tipoMime()).isEqualTo("image/jpeg");
		assertThat(pedido.fotos().get(0).bytes()).containsExactly(1, 2, 3);
		assertThat(pedido.tipos()).extracting(t -> t.codigo()).containsExactly("BACHE", "LUZ");
	}

	@Test
	void lasJustificacionesSeGuardanSinEspaciosDeMasYUnaDe300SeAcepta() {
		asistente.respuesta = p -> new ResultadoIa.Hecha("LUZ", PrioridadIa.BAJA, "  " + "a".repeat(300) + "  ", null, null);

		servicio(200).analizar(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.HECHA);
		assertThat(fila.getJustificacion()).hasSize(300);
		assertThat(fila.getTokensEntrada()).isNull();
	}

	// ---- ERROR

	@Test
	void siElProveedorLanzaEsUnErrorYNoSePropaga() {
		asistente.respuesta = p -> {
			throw new IllegalStateException("se cayó, y acá iría el texto: " + DESCRIPCION_SECRETA);
		};

		servicio(200).alCrearElReclamo(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.ERROR);
		assertThat(fila.getDetalleError()).contains("IllegalStateException").doesNotContain("Evergreen");
		assertThat(fila.getTipoSugerido()).isNull();
		assertThat(fila.getPrioridadSugerida()).isNull();
		assertThat(fila.getJustificacion()).isNull();
		assertThat(fila.getProveedor()).isEqualTo(ProveedorIa.CLAUDE);
	}

	@Test
	void siElProveedorNoDevuelveNadaEsUnError() {
		asistente.respuesta = p -> null;

		servicio(200).analizar(reclamo);

		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.ERROR);
	}

	@Test
	void unaFallaDelProveedorSeGuardaComoErrorConSuDetalleYSusTokens() {
		asistente.respuesta = p -> new ResultadoIa.Fallida("El proveedor se negó a responder (refusal).", 900L, 3L);

		servicio(200).analizar(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.ERROR);
		assertThat(fila.getDetalleError()).isEqualTo("El proveedor se negó a responder (refusal).");
		assertThat(fila.getTokensEntrada()).isEqualTo(900L);
		assertThat(fila.getTokensSalida()).isEqualTo(3L);
	}

	@Test
	void elDetalleDelErrorSeRecortaA300Caracteres() {
		asistente.respuesta = p -> new ResultadoIa.Fallida("d".repeat(500), null, null);

		servicio(200).analizar(reclamo);

		assertThat(guardada().getDetalleError()).hasSize(300);
	}

	@Test
	void unaRespuestaQueNoValidaEsUnErrorYNoSeGuardaComoSugerencia() {
		List<ResultadoIa> basura = List.of(
				new ResultadoIa.Hecha("INVENTADO", PrioridadIa.ALTA, "Tipo que no existe.", 10L, 5L),
				new ResultadoIa.Hecha(null, PrioridadIa.ALTA, "Sin tipo.", 10L, 5L),
				new ResultadoIa.Hecha("BACHE", null, "Sin prioridad.", 10L, 5L),
				new ResultadoIa.Hecha("BACHE", PrioridadIa.ALTA, "x".repeat(301), 10L, 5L),
				new ResultadoIa.Hecha("BACHE", PrioridadIa.ALTA, "   ", 10L, 5L),
				new ResultadoIa.Hecha("BACHE", PrioridadIa.ALTA, null, 10L, 5L));
		for (ResultadoIa respuesta : basura) {
			SugerenciaIaRepository repo = mock(SugerenciaIaRepository.class);
			when(repo.contarAnalisisDesde(anyLong(), any())).thenReturn(0L);
			asistente.respuesta = p -> respuesta;

			new AnalisisIaService(asistente, ejecutor, ejecutorDeOmisiones, repo, fotos, tipos, transacciones, reloj, "200", "1000").analizar(reclamo);

			ArgumentCaptor<SugerenciaIa> captor = ArgumentCaptor.forClass(SugerenciaIa.class);
			verify(repo).save(captor.capture());
			assertThat(captor.getValue().getEstado()).as(respuesta.toString()).isEqualTo(EstadoSugerenciaIa.ERROR);
			assertThat(captor.getValue().getDetalleError()).contains("validación");
			assertThat(captor.getValue().getTipoSugerido()).isNull();
		}
	}

	@Test
	void siNoSePuedenLeerLasFotosEsUnError() {
		when(fotos.delVecinoDe(70L)).thenThrow(new IllegalStateException("base caída"));

		servicio(200).analizar(reclamo);

		assertThat(asistente.pedidos).isEmpty();
		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.ERROR);
	}

	// ---- OMITIDA

	@Test
	void sinProveedorDisponibleQuedaOmitidaYNoSeLlamaANadie() {
		asistente.disponible = false;

		servicio(200).alCrearElReclamo(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
		assertThat(fila.getProveedor()).isEqualTo(ProveedorIa.NINGUNO);
		assertThat(fila.getModelo()).isNull();
		assertThat(fila.getTipoSugerido()).isNull();
		assertThat(asistente.pedidos).isEmpty();
		verify(sugerencias, never()).contarAnalisisDesde(anyLong(), any());
		verify(fotos, never()).delVecinoDe(anyLong());
	}

	@Test
	void siElProveedorDiceOmitidaQuedaOmitida() {
		asistente.respuesta = p -> new ResultadoIa.Omitida();

		servicio(200).analizar(reclamo);

		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
	}

	@Test
	void conElTopeDiarioSuperadoQuedaOmitidaSinLlamarAlProveedorYSeAvisaEnElLog() {
		when(sugerencias.contarAnalisisDesde(anyLong(), any())).thenReturn(200L);

		servicio(200).alCrearElReclamo(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
		assertThat(asistente.pedidos).isEmpty();
		assertThat(registro.list).anyMatch(e -> e.getFormattedMessage().contains("Tope diario (de la comuna)")
				&& e.getFormattedMessage().contains("salsipuedes") && e.getFormattedMessage().contains("12"));
	}

	@Test
	void unAnalisisMenosQueElTopeTodaviaSeHace() {
		when(sugerencias.contarAnalisisDesde(anyLong(), any())).thenReturn(199L);

		servicio(200).analizar(reclamo);

		assertThat(asistente.pedidos).hasSize(1);
		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.HECHA);
	}

	@Test
	void elTopeSeCuentaPorComunaYDesdeElComienzoDelDiaArgentino() {
		servicio(200).analizar(reclamo);

		ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
		verify(sugerencias).contarAnalisisDesde(eq(7L), desde.capture());
		// A las 23:30 del 22/11 en Argentina, el día empezó a las 00:00 del 22/11 (03:00 UTC).
		assertThat(desde.getValue()).isEqualTo(Instant.parse("2026-11-22T03:00:00Z"));
	}

	@Test
	void conElTopeGlobalSuperadoQuedaOmitidaAunqueLaComunaNoLleguePorSuTopeYSeCuentanTodasLasComunas() {
		when(sugerencias.contarAnalisisDesde(anyLong(), any())).thenReturn(5L);
		when(sugerencias.contarAnalisisDeTodasLasComunasDesde(any())).thenReturn(1000L);

		servicio(200, 1000).alCrearElReclamo(reclamo);

		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
		assertThat(asistente.pedidos).isEmpty();
		assertThat(registro.list).anyMatch(e -> e.getFormattedMessage().contains("Tope diario (global)")
				&& e.getFormattedMessage().contains("salsipuedes") && e.getFormattedMessage().contains("12"));
		ArgumentCaptor<Instant> desde = ArgumentCaptor.forClass(Instant.class);
		verify(sugerencias).contarAnalisisDeTodasLasComunasDesde(desde.capture());
		assertThat(desde.getValue()).isEqualTo(Instant.parse("2026-11-22T03:00:00Z"));
	}

	@Test
	void unAnalisisMenosQueElTopeGlobalTodaviaSeHace() {
		when(sugerencias.contarAnalisisDeTodasLasComunasDesde(any())).thenReturn(999L);

		servicio(200, 1000).analizar(reclamo);

		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.HECHA);
	}

	@Test
	void conTopeGlobalCeroTodoQuedaOmitido() {
		servicio(200, 0).analizar(reclamo);

		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
		assertThat(asistente.pedidos).isEmpty();
	}

	@Test
	void laJustificacionSeGuardaSinCaracteresDeControlNiEspaciosDeLosExtremos() {
		asistente.respuesta = p -> new ResultadoIa.Hecha("LUZ", PrioridadIa.BAJA,
				"\n  Se ve\tun farol\0 apagado\r\n\u202Eoculto  ", 1L, 1L);

		servicio(200).analizar(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.HECHA);
		assertThat(fila.getJustificacion()).isEqualTo("Se veun farol apagadooculto");
	}

	@Test
	void unaJustificacionQueQuedaVaciaTrasLimpiarlaEsUnError() {
		asistente.respuesta = p -> new ResultadoIa.Hecha("LUZ", PrioridadIa.BAJA, " \n\t\0\u200B ", 1L, 1L);

		servicio(200).analizar(reclamo);

		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.ERROR);
		assertThat(fila.getJustificacion()).isNull();
	}

	@Test
	void conTopeCeroTodoQuedaOmitido() {
		servicio(0).analizar(reclamo);

		assertThat(guardada().getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
		assertThat(asistente.pedidos).isEmpty();
	}

	@Test
	void conLaColaLlenaQuedaOmitidaYElLogDiceNumeroYComuna() {
		ejecutor = tarea -> {
			throw new RejectedExecutionException("cola llena");
		};
		java.util.List<Runnable> deOmisiones = new java.util.ArrayList<>();
		ejecutorDeOmisiones = deOmisiones::add;

		servicio(200).alCrearElReclamo(reclamo);

		// La fila NO se escribió en el hilo que confirmó el alta: quedó encolada para el ejecutor de omisiones.
		verify(sugerencias, never()).save(any());
		assertThat(deOmisiones).hasSize(1);
		deOmisiones.get(0).run();
		SugerenciaIa fila = guardada();
		assertThat(fila.getEstado()).isEqualTo(EstadoSugerenciaIa.OMITIDA);
		assertThat(fila.getIdReclamo()).isEqualTo(70L);
		assertThat(asistente.pedidos).isEmpty();
		assertThat(registro.list).anyMatch(e -> e.getFormattedMessage().contains("Cola de análisis de IA llena")
				&& e.getFormattedMessage().contains("n.º 12") && e.getFormattedMessage().contains("salsipuedes"));
	}

	@Test
	void conLaColaLlenaYElEjecutorDeOmisionesTambienLlenoSoloQuedaElLog() {
		ejecutor = tarea -> {
			throw new RejectedExecutionException("cola llena");
		};
		ejecutorDeOmisiones = tarea -> {
			throw new RejectedExecutionException("también llena");
		};

		servicio(200).alCrearElReclamo(reclamo);

		verify(sugerencias, never()).save(any());
		assertThat(registro.list).anyMatch(e -> e.getFormattedMessage().contains("Tampoco hay lugar")
				&& e.getFormattedMessage().contains("n.º 12") && e.getFormattedMessage().contains("salsipuedes"));
	}

	@Test
	void conLaColaLlenaYLaBaseCaidaTampocoFalla() {
		ejecutor = tarea -> {
			throw new RejectedExecutionException("cola llena");
		};
		when(sugerencias.save(any())).thenThrow(new IllegalStateException("base caída"));

		servicio(200).alCrearElReclamo(reclamo);

		assertThat(registro.list).anyMatch(e -> e.getFormattedMessage().contains("No se pudo guardar la sugerencia"));
	}

	// ---- Que nunca falle ni filtre datos

	@Test
	void siNoSePuedeGuardarLaFilaSoloSeRegistra() {
		when(sugerencias.save(any())).thenThrow(new IllegalStateException("reclamo borrado"));

		servicio(200).alCrearElReclamo(reclamo);

		verify(sugerencias, times(1)).save(any());
		assertThat(registro.list).anyMatch(e -> e.getFormattedMessage().contains("No se pudo guardar la sugerencia")
				&& e.getFormattedMessage().contains("n.º 12"));
	}

	@Test
	void ningunLogLlevaLaDescripcionNiElDetalleDelError() {
		asistente.respuesta = p -> {
			throw new IllegalStateException(DESCRIPCION_SECRETA);
		};
		when(sugerencias.contarAnalisisDesde(anyLong(), any())).thenReturn(0L);

		servicio(200).alCrearElReclamo(reclamo);
		servicio(200).analizar(reclamo);

		assertThat(registro.list).isNotEmpty().noneMatch(e -> e.getFormattedMessage().contains("Evergreen")
				|| e.getFormattedMessage().contains("351-555"));
		assertThat(reclamo.toString()).doesNotContain("Evergreen");
		assertThat(asistente.pedidos.get(0).toString()).doesNotContain("Evergreen");
	}

	@Test
	void alCrearElReclamoSoloEntregaElTrabajoAlEjecutorYNoEsperaAlProveedor() {
		java.util.List<Runnable> entregadas = new java.util.ArrayList<>();
		ejecutor = entregadas::add;

		servicio(200).alCrearElReclamo(reclamo);

		assertThat(entregadas).hasSize(1);
		assertThat(asistente.pedidos).isEmpty();
		verify(sugerencias, never()).save(any());

		entregadas.get(0).run();

		assertThat(asistente.pedidos).hasSize(1);
		verify(sugerencias).save(any());
	}
}
