package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.micomuna.dtos.CambioDeEstadoEnLoteRequest;
import com.micomuna.dtos.DescarteEnLoteRequest;
import com.micomuna.dtos.ResultadoEnLoteDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.CodigoOmitido;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.TransicionInvalidaException;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.security.ComunaActual;

// El bucle de los lotes con un servicio de cambio simulado: ahí se pueden provocar fallos que contra la base real
// no se dejan armar (carreras, errores inesperados, tope de tiempo).
class CambiosEnLoteServiceTests {

	private CambioDeEstadoService cambios;
	private ReclamoRepository reclamos;
	private CambiosEnLoteService lotes;

	@BeforeEach
	void preparar() {
		cambios = mock(CambioDeEstadoService.class);
		reclamos = mock(ReclamoRepository.class);
		ComunaActual comunaActual = mock(ComunaActual.class);
		when(comunaActual.id()).thenReturn(1L);
		// Transacciones de mentira: no hay base, solo se ejecuta el código de cada ítem.
		PlatformTransactionManager transacciones = new PlatformTransactionManager() {
			@Override
			public TransactionStatus getTransaction(TransactionDefinition definicion) {
				return new SimpleTransactionStatus();
			}

			@Override
			public void commit(TransactionStatus estado) {
			}

			@Override
			public void rollback(TransactionStatus estado) {
			}
		};
		lotes = new CambiosEnLoteService(cambios, reclamos, comunaActual, transacciones);
	}

	private ResultadoEnLoteDto cambiar(Integer... numeros) {
		return lotes.cambiarEstado(new CambioDeEstadoEnLoteRequest(List.of(numeros), EstadoReclamo.RECIBIDO, null), 5L);
	}

	@Test
	void unErrorInesperadoOUnFalloDeDatosMarcaEseItemComoErrorYNoPierdeLosAplicados() {
		doThrow(new IllegalStateException("algo raro")).when(cambios).cambiar(eq(2), any(), any());
		doThrow(new DataIntegrityViolationException("rompió")).when(cambios).cambiar(eq(3), any(), any());

		ResultadoEnLoteDto resultado = cambiar(1, 2, 3, 4);

		assertThat(resultado.aplicados()).containsExactly(1, 4);
		assertThat(resultado.omitidos()).extracting(o -> o.numero(), o -> o.codigo())
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2, CodigoOmitido.ERROR),
						org.assertj.core.groups.Tuple.tuple(3, CodigoOmitido.ERROR));
		assertThat(resultado.omitidos().get(0).motivo()).startsWith("No se pudo cambiar el n.º 2");
	}

	@Test
	void unAgenteOUnOriginalQueDejaronDeSerValidosSeInformanPorItem() {
		doThrow(new PeticionInvalidaException("AGENTE_INVALIDO", "x")).when(cambios).cambiar(eq(1), any(), any());
		doThrow(new PeticionInvalidaException("ORIGINAL_INVALIDO", "x")).when(cambios).cambiar(eq(2), any(), any());
		doThrow(new PeticionInvalidaException("TEXTO_MUY_LARGO", "x")).when(cambios).cambiar(eq(3), any(), any());

		ResultadoEnLoteDto resultado = cambiar(1, 2, 3, 4);

		assertThat(resultado.aplicados()).containsExactly(4);
		assertThat(resultado.omitidos()).extracting(o -> o.codigo()).containsExactly(CodigoOmitido.AGENTE_INVALIDO,
				CodigoOmitido.ORIGINAL_INVALIDO, CodigoOmitido.ERROR);
		assertThat(resultado.omitidos().get(0).motivo()).contains("n.º 1");
	}

	@Test
	void sePasaElTopeTotalLosQueFaltanSalenComoConflictoConSuTexto() {
		lotes.topeTotal = Duration.ZERO;

		ResultadoEnLoteDto resultado = cambiar(1, 2);

		assertThat(resultado.aplicados()).isEmpty();
		assertThat(resultado.omitidos()).extracting(o -> o.codigo())
				.containsExactly(CodigoOmitido.CONFLICTO, CodigoOmitido.CONFLICTO);
		assertThat(resultado.omitidos()).extracting(o -> o.motivo())
				.containsOnly("No se llegó a procesar: probá de nuevo.");
	}

	@Test
	void conElTopeHolgadoSeProcesaTodo() {
		assertThat(cambiar(1, 2, 3).aplicados()).containsExactly(1, 2, 3);
	}

	@Test
	void unoYaAsignadoAOtroAgenteDiceAQuienYAlMismoAgenteElTextoDeSiempre() {
		doThrow(new TransicionInvalidaException(EstadoReclamo.ASIGNADO, EstadoReclamo.ASIGNADO)).when(cambios)
				.cambiar(any(), any(), any());
		Usuario actual = new Usuario();
		actual.setNombre("Pedro Agente");
		ReflectionTestUtils.setField(actual, "id", 7L);
		when(reclamos.agenteDe(1L, 1)).thenReturn(Optional.of(actual));
		when(reclamos.agenteDe(1L, 2)).thenReturn(Optional.of(actual));
		when(reclamos.agenteDe(1L, 3)).thenReturn(Optional.empty());

		ResultadoEnLoteDto otro = lotes.cambiarEstado(
				new CambioDeEstadoEnLoteRequest(List.of(1, 3), EstadoReclamo.ASIGNADO, 9L), 5L);
		assertThat(otro.omitidos().get(0).motivo()).isEqualTo("El n.º 1 ya está asignado a Pedro Agente.");
		assertThat(otro.omitidos().get(0).codigo()).isEqualTo(CodigoOmitido.YA_ESTABA_EN_ESE_ESTADO);
		assertThat(otro.omitidos().get(1).motivo()).isEqualTo("El n.º 3 ya estaba en ese estado.");

		ResultadoEnLoteDto mismo = lotes.cambiarEstado(
				new CambioDeEstadoEnLoteRequest(List.of(2), EstadoReclamo.ASIGNADO, 7L), 5L);
		assertThat(mismo.omitidos().get(0).motivo()).isEqualTo("El n.º 2 ya estaba en ese estado.");
	}

	@Test
	void tambienEnDescartesUnErrorInesperadoNoFrenaAlResto() {
		Reclamo original = new Reclamo();
		original.setEstado(EstadoReclamo.RECIBIDO);
		when(reclamos.findByComunaIdAndNumero(1L, 50)).thenReturn(Optional.of(original));
		doThrow(new IllegalStateException("x")).when(cambios).cambiar(eq(1), any(), any());

		ResultadoEnLoteDto resultado = lotes.descartar(
				new DescarteEnLoteRequest(List.of(1, 2), MotivoDescarte.DUPLICADO, null, 50), 5L);

		assertThat(resultado.aplicados()).containsExactly(2);
		assertThat(resultado.omitidos().get(0).codigo()).isEqualTo(CodigoOmitido.ERROR);
	}
}
