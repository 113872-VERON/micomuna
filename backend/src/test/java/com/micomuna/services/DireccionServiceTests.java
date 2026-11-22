package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.micomuna.client.NominatimClient;
import com.micomuna.exceptions.LimiteSuperadoException;

// Caché y topes de pedidos (global y por usuario), con un reloj falso (nadie espera de verdad) y el cliente simulado.
class DireccionServiceTests {

	private static final long SEGUNDO = 1_000_000_000L;
	private static final long USUARIO = 1L;

	private NominatimClient cliente;
	private AtomicLong reloj;
	private DireccionService servicio;

	@BeforeEach
	void preparar() {
		cliente = mock(NominatimClient.class);
		reloj = new AtomicLong(1_000 * SEGUNDO);
		servicio = new DireccionService(cliente, reloj::get);
		when(cliente.direccionDe(any(), any())).thenReturn(Optional.of("Calle 1"));
	}

	private void pasar(long segundos) {
		reloj.addAndGet(segundos * SEGUNDO);
	}

	private Optional<String> pedir(long usuario, double latitud, double longitud) {
		return servicio.direccionDe(usuario, latitud, longitud);
	}

	private void esperarRechazo(long usuario, double latitud) {
		assertThatThrownBy(() -> pedir(usuario, latitud, -64.1))
				.isInstanceOfSatisfying(LimiteSuperadoException.class,
						e -> assertThat(e.getCodigo()).isEqualTo("DEMASIADOS_PEDIDOS"));
	}

	// ---- Caché

	@Test
	void elPrimerPedidoSaleAlServicio() {
		assertThat(pedir(USUARIO, -31.42008, -64.18878)).contains("Calle 1");
		verify(cliente).direccionDe(new BigDecimal("-31.42008"), new BigDecimal("-64.18878"));
	}

	@Test
	void elSegundoPedidoDelMismoPuntoSaleDeLaCacheYNoVaALaRed() {
		pedir(USUARIO, -31.42008, -64.18878);
		// Sin pasar el tiempo: de la red no podría salir, pero la caché responde igual.
		assertThat(pedir(USUARIO, -31.42008, -64.18878)).contains("Calle 1");
		verify(cliente, times(1)).direccionDe(any(), any());
	}

	@Test
	void puntosQueRedondeanIgualA5DecimalesComparteLaCache() {
		pedir(USUARIO, -31.420081, -64.188776);
		pedir(USUARIO, -31.420084, -64.188779);
		verify(cliente, times(1)).direccionDe(new BigDecimal("-31.42008"), new BigDecimal("-64.18878"));
		// Un punto de otro metro ya es otra entrada.
		pasar(2);
		pedir(USUARIO, -31.42009, -64.18878);
		verify(cliente, times(2)).direccionDe(any(), any());
	}

	@Test
	void unSinResultadoNoSeRecuerdaYSeReintenta() {
		when(cliente.direccionDe(any(), any())).thenReturn(Optional.empty(), Optional.of("Calle 2"));
		assertThat(pedir(USUARIO, -31.4, -64.1)).isEmpty();
		pasar(2);
		assertThat(pedir(USUARIO, -31.4, -64.1)).contains("Calle 2");
		verify(cliente, times(2)).direccionDe(any(), any());
	}

	@Test
	void laCacheTieneTopeYDescartaLaMasVieja() {
		when(cliente.direccionDe(any(), any())).thenAnswer(i -> Optional.of("dir " + i.getArgument(1)));
		// Un usuario distinto por pedido, para no chocar con el tope por usuario.
		for (int i = 0; i < DireccionService.CACHE_MAXIMA + 1; i++) {
			pasar(2);
			pedir(i, -31.0, -60.0 - i * 0.00001);
		}
		assertThat(servicio.tamanoDeLaCache()).isEqualTo(DireccionService.CACHE_MAXIMA);

		int antes = DireccionService.CACHE_MAXIMA + 1;
		verify(cliente, times(antes)).direccionDe(any(), any());
		// La última sigue en la caché; la primera se descartó y sale otra vez a la red.
		pedir(USUARIO, -31.0, -60.0 - DireccionService.CACHE_MAXIMA * 0.00001);
		verify(cliente, times(antes)).direccionDe(any(), any());
		pasar(2);
		pedir(USUARIO, -31.0, -60.0);
		verify(cliente, times(antes + 1)).direccionDe(any(), any());
	}

	// ---- Tope global: un pedido por segundo, sin esperar

	@Test
	void sinTurnoDisponibleRechazaEnseguidaSinEsperarYSinLlamarAlServicio() {
		pedir(1, -31.1, -64.1);
		// Otro usuario, en el mismo instante: no hay turno. Responde 429 sin dormir (el reloj no se movió).
		long antes = reloj.get();
		esperarRechazo(2, -31.2);
		assertThat(reloj.get()).isEqualTo(antes);
		verify(cliente, times(1)).direccionDe(any(), any());
		// Pasado un poco menos de un segundo, todavía no.
		reloj.addAndGet(SEGUNDO - 1);
		esperarRechazo(2, -31.2);
		// Con el segundo cumplido, sí.
		reloj.addAndGet(1);
		assertThat(pedir(2, -31.2, -64.1)).contains("Calle 1");
	}

	@Test
	void unRechazoDelTopeGlobalNoGastaElTurno() {
		pedir(1, -31.1, -64.1);
		esperarRechazo(2, -31.2);
		pasar(1);
		// El rechazado no movió el turno: al segundo del pedido que sí salió, pasa.
		assertThat(pedir(3, -31.3, -64.1)).contains("Calle 1");
	}

	@Test
	void lasRespuestasDeLaCacheNoConsumenTurnos() {
		pedir(1, -31.1, -64.1);
		for (int i = 0; i < 50; i++) {
			assertThat(pedir(2, -31.1, -64.1)).contains("Calle 1");
		}
		pasar(1);
		assertThat(pedir(2, -31.2, -64.1)).contains("Calle 1");
	}

	// ---- Tope por usuario: 10 por minuto

	private void diezPedidosDeUnUsuario(long usuario) {
		for (int i = 0; i < 10; i++) {
			pasar(1);
			pedir(usuario, -31.0 - i * 0.1, -64.1);
		}
	}

	@Test
	void elUndecimoPedidoEnUnMinutoDa429YNoSaleALaRed() {
		diezPedidosDeUnUsuario(USUARIO);
		verify(cliente, times(10)).direccionDe(any(), any());

		pasar(1);
		esperarRechazo(USUARIO, -35.0);
		verify(cliente, times(10)).direccionDe(any(), any());
	}

	@Test
	void otroUsuarioNoSeVeAfectado() {
		diezPedidosDeUnUsuario(USUARIO);
		pasar(1);
		esperarRechazo(USUARIO, -35.0);
		assertThat(pedir(2, -35.0, -64.1)).contains("Calle 1");
	}

	@Test
	void losAciertosDeCacheNoCuentanParaElTopeDelUsuario() {
		diezPedidosDeUnUsuario(USUARIO);
		// Ya hizo 10 pedidos a la red, pero puede seguir pidiendo puntos que están en la caché.
		for (int i = 0; i < 10; i++) {
			assertThat(pedir(USUARIO, -31.0 - i * 0.1, -64.1)).contains("Calle 1");
		}
		verify(cliente, times(10)).direccionDe(any(), any());
	}

	@Test
	void despuesDeUnMinutoElUsuarioVuelveAPoder() {
		diezPedidosDeUnUsuario(USUARIO);
		pasar(1);
		esperarRechazo(USUARIO, -35.0);
		// Un minuto después de su primer pedido la ventana se liberó.
		pasar(60);
		assertThat(pedir(USUARIO, -35.0, -64.1)).contains("Calle 1");
	}

	@Test
	void unRechazoDelGlobalNoAfectaAlUsuarioQueYaPidio() {
		diezPedidosDeUnUsuario(USUARIO);
		// Sin pasar el tiempo: el global también diría que no, pero el usuario ya está al tope en cualquier caso.
		// Un pedido rechazado por el global (otro usuario) no cuenta contra el tope del primero.
		esperarRechazo(2, -40.0);
		pasar(1);
		assertThat(pedir(2, -40.0, -64.1)).contains("Calle 1");
	}

	// ---- Los limitadores por separado

	@Test
	void elLimitadorPorUsuarioDescartaALosInactivosYTieneTope() {
		AtomicLong hora = new AtomicLong(0);
		LimitadorPorUsuario limitador = new LimitadorPorUsuario(hora::get);
		for (long id = 0; id < LimitadorPorUsuario.USUARIOS_MAXIMOS; id++) {
			limitador.registrar(id);
		}
		assertThat(limitador.usuariosAnotados()).isEqualTo(LimitadorPorUsuario.USUARIOS_MAXIMOS);
		// Todos activos: un usuario nuevo no entra (y el tamaño no crece).
		assertThatThrownBy(() -> limitador.registrar(-1L)).isInstanceOf(LimiteSuperadoException.class);
		assertThat(limitador.usuariosAnotados()).isEqualTo(LimitadorPorUsuario.USUARIOS_MAXIMOS);
		// Un usuario ya anotado sigue pudiendo pedir.
		limitador.registrar(0L);

		// Pasado el minuto, los inactivos se descartan y el nuevo entra.
		hora.addAndGet(LimitadorPorUsuario.VENTANA_NANOS);
		limitador.registrar(-1L);
		assertThat(limitador.usuariosAnotados()).isEqualTo(1);
	}
}
