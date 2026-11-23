package com.micomuna.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.CambioDeEstadoEnLoteRequest;
import com.micomuna.dtos.DescarteEnLoteRequest;
import com.micomuna.dtos.OmitidoDto;
import com.micomuna.dtos.ResultadoEnLoteDto;
import com.micomuna.enums.CodigoOmitido;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.exceptions.TransicionInvalidaException;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.entities.Usuario;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.security.ComunaActual;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;

// Cambios de estado y descartes de varios reclamos a la vez desde el panel (MC-18). Lo que sirve para todos se revisa
// UNA vez al principio (si falla, 400 y no se toca nada). Después cada reclamo se cambia por separado, en su propia
// transacción y con el bloqueo de CambioDeEstadoService: uno que no se puede no frena a los demás, se informa en
// "omitidos" con un código y un texto para mostrar.
// Este servicio NO controla roles: el controlador exige ADMINISTRATIVO o ADMIN_COMUNA, y idUsuario sale del token.
@Service
public class CambiosEnLoteService {

	private static final Logger log = LoggerFactory.getLogger(CambiosEnLoteService.class);
	public static final int MAXIMO_RECLAMOS = 100;
	// Tiempo máximo de cada reclamo (incluye hasta 3 segundos de espera de su bloqueo).
	private static final int SEGUNDOS_MAXIMOS_POR_RECLAMO = 10;

	private final CambioDeEstadoService cambios;
	private final ReclamoRepository reclamos;
	private final ComunaActual comunaActual;
	private final TransactionTemplate transaccion;
	// Tope de tiempo de todo el lote; lo que no se llega a procesar se informa como CONFLICTO. Se cambia solo en pruebas.
	Duration topeTotal = Duration.ofSeconds(30);

	public CambiosEnLoteService(CambioDeEstadoService cambios, ReclamoRepository reclamos, ComunaActual comunaActual,
			PlatformTransactionManager transacciones) {
		this.cambios = cambios;
		this.reclamos = reclamos;
		this.comunaActual = comunaActual;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setTimeout(SEGUNDOS_MAXIMOS_POR_RECLAMO);
	}

	public ResultadoEnLoteDto cambiarEstado(CambioDeEstadoEnLoteRequest pedido, Long idUsuario) {
		revisarNumeros(pedido.numeros());
		EstadoReclamo estado = pedido.estado();
		if (estado == EstadoReclamo.DESCARTADO) {
			throw new PeticionInvalidaException("VALIDACION", "Para descartar usá la opción de descartar.");
		}
		// ASIGNADO exige un agente activo de la comuna; con otro estado no se acepta ninguno. Antes de tocar nada.
		cambios.validarAgente(estado, pedido.idAgente());

		CambioDeEstado cambio = estado == EstadoReclamo.ASIGNADO ? CambioDeEstado.asignar(pedido.idAgente())
				: CambioDeEstado.a(estado);
		return aplicar(pedido.numeros(), cambio, idUsuario, false);
	}

	public ResultadoEnLoteDto descartar(DescarteEnLoteRequest pedido, Long idUsuario) {
		revisarNumeros(pedido.numeros());
		CambioDeEstado cambio = CambioDeEstado.descartar(pedido.motivo(), pedido.observacion(), pedido.numeroOriginal());
		CambioDeEstadoService.validarDescarte(cambio);
		if (pedido.motivo() == MotivoDescarte.DUPLICADO) {
			// El original se revisa una sola vez: de la comuna, no descartado y que no se esté descartando en este mismo
			// lote (si no, quedaría apuntando a un reclamo que también se descarta).
			boolean valido = !pedido.numeros().contains(pedido.numeroOriginal())
					&& reclamos.findByComunaIdAndNumero(comunaActual.id(), pedido.numeroOriginal())
							.filter(o -> o.getEstado() != EstadoReclamo.DESCARTADO).isPresent();
			if (!valido) {
				throw new PeticionInvalidaException("ORIGINAL_INVALIDO",
						"El reclamo original tiene que existir en la comuna, no estar descartado ni estar en la lista.");
			}
		}
		return aplicar(pedido.numeros(), cambio, idUsuario, true);
	}

	private static void revisarNumeros(List<Integer> numeros) {
		if (numeros == null || numeros.isEmpty() || numeros.size() > MAXIMO_RECLAMOS) {
			throw new PeticionInvalidaException("VALIDACION",
					"Elegí entre 1 y " + MAXIMO_RECLAMOS + " reclamos.");
		}
		if (new HashSet<>(numeros).size() != numeros.size()) {
			throw new PeticionInvalidaException("VALIDACION", "Hay números de reclamo repetidos.");
		}
	}

	private ResultadoEnLoteDto aplicar(List<Integer> numeros, CambioDeEstado cambio, Long idUsuario,
			boolean esDescarte) {
		List<Integer> aplicados = new ArrayList<>();
		List<OmitidoDto> omitidos = new ArrayList<>();
		long limite = System.nanoTime() + topeTotal.toNanos();
		for (Integer numero : numeros) {
			// Tope total del lote: si se agotó, lo que falta no se procesa y se informa para que el personal reintente.
			if (System.nanoTime() - limite >= 0) {
				omitidos.add(omitido(numero, CodigoOmitido.CONFLICTO, "No se llegó a procesar: probá de nuevo."));
				continue;
			}
			try {
				// Una transacción por reclamo: el servicio se une a ella y su bloqueo dura hasta que termina.
				transaccion.executeWithoutResult(estado -> cambios.cambiar(numero, cambio, idUsuario));
				aplicados.add(numero);
			} catch (RecursoNoEncontradoException e) {
				omitidos.add(omitido(numero, CodigoOmitido.NO_ENCONTRADO, "No encontramos el n.º " + numero + "."));
			} catch (TransicionInvalidaException e) {
				omitidos.add(porTransicion(numero, e, esDescarte, cambio));
			} catch (PessimisticLockingFailureException | LockTimeoutException | PessimisticLockException
					| TransactionTimedOutException | QueryTimeoutException e) {
				omitidos.add(omitido(numero, CodigoOmitido.CONFLICTO,
						"El n.º " + numero + " lo está cambiando otra persona. Probá de nuevo en unos segundos."));
			} catch (PeticionInvalidaException e) {
				// El agente o el original eran válidos al empezar y dejaron de serlo (otra persona los cambió).
				omitidos.add(porDatosInvalidos(numero, e));
			} catch (DataAccessException e) {
				log.error("Falló el acceso a datos al cambiar el n.º {} en un lote", numero, e);
				omitidos.add(error(numero));
			} catch (RuntimeException e) {
				log.error("Error inesperado al cambiar el n.º {} en un lote", numero, e);
				omitidos.add(error(numero));
			}
		}
		return new ResultadoEnLoteDto(aplicados, omitidos);
	}

	private static OmitidoDto porDatosInvalidos(Integer numero, PeticionInvalidaException e) {
		return switch (e.getCodigo()) {
			case "AGENTE_INVALIDO" -> omitido(numero, CodigoOmitido.AGENTE_INVALIDO,
					"No se pudo asignar el n.º " + numero + ": el agente ya no está disponible.");
			case "ORIGINAL_INVALIDO" -> omitido(numero, CodigoOmitido.ORIGINAL_INVALIDO,
					"No se pudo descartar el n.º " + numero + ": el reclamo original ya no es válido.");
			default -> error(numero);
		};
	}

	private static OmitidoDto error(Integer numero) {
		return omitido(numero, CodigoOmitido.ERROR,
				"No se pudo cambiar el n.º " + numero + " por un problema inesperado. Probá de nuevo.");
	}

	private OmitidoDto porTransicion(Integer numero, TransicionInvalidaException e, boolean esDescarte,
			CambioDeEstado cambio) {
		EstadoReclamo desde = e.getDesde();
		if (esDescarte && (desde == EstadoReclamo.RESUELTO || desde == EstadoReclamo.DESCARTADO)) {
			return omitido(numero, CodigoOmitido.YA_CERRADO, "El n.º " + numero + " ya está cerrado.");
		}
		if (desde == e.getHacia()) {
			// Asignar a otro agente un reclamo que ya está asignado: se dice a quién (reasignar es de otra historia).
			if (desde == EstadoReclamo.ASIGNADO) {
				Usuario actual = reclamos.agenteDe(comunaActual.id(), numero).orElse(null);
				if (actual != null && !actual.getId().equals(cambio.idAgente())) {
					return omitido(numero, CodigoOmitido.YA_ESTABA_EN_ESE_ESTADO,
							"El n.º " + numero + " ya está asignado a " + actual.getNombre() + ".");
				}
			}
			return omitido(numero, CodigoOmitido.YA_ESTABA_EN_ESE_ESTADO,
					"El n.º " + numero + " ya estaba en ese estado.");
		}
		return omitido(numero, CodigoOmitido.TRANSICION_INVALIDA, "El n.º " + numero + " no puede pasar de «"
				+ nombre(desde) + "» a «" + nombre(e.getHacia()) + "».");
	}

	private static OmitidoDto omitido(Integer numero, CodigoOmitido codigo, String motivo) {
		return new OmitidoDto(Objects.requireNonNull(numero), codigo, motivo);
	}

	private static String nombre(EstadoReclamo estado) {
		return switch (estado) {
			case RECIBIDO -> "Recibido";
			case ASIGNADO -> "Asignado";
			case EN_REPARACION -> "En reparación";
			case RESUELTO -> "Resuelto";
			case DESCARTADO -> "Descartado";
		};
	}
}
