package com.micomuna.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.ReclamoCerradoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.exceptions.YaAsignadoException;
import com.micomuna.exceptions.TransicionInvalidaException;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;

import lombok.RequiredArgsConstructor;

// El único lugar donde un reclamo cambia de estado (MC-65). Valida que la transición esté permitida
// (EstadoReclamo.siguientesPermitidos), actualiza el reclamo y escribe la fila del historial (RECLAMO_ESTADO: de qué
// estado a cuál, quién y cuándo) en la misma transacción: o quedan las dos cosas o ninguna.
// La comuna sale de ComunaActual y el reclamo se busca por (comuna, número): uno de otra comuna es un 404.
// Lo usan las historias que cambian estados (MC-18 a MC-20); no tiene endpoint propio.
@Service
@RequiredArgsConstructor
public class CambioDeEstadoService {

	public static final int OBSERVACION_MAXIMA = 500;
	// El vecino lee la observación del descarte: se mantiene corta.
	public static final int OBSERVACION_DESCARTE_MAXIMA = 300;
	public static final int TRABAJO_MAXIMO = 1000;
	// Tiempo máximo de la transacción, que incluye hasta 3 segundos de espera del bloqueo de la fila del reclamo.
	static final int SEGUNDOS_MAXIMOS = 10;

	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";

	private final ReclamoRepository reclamos;
	private final ReclamoEstadoRepository historial;
	private final UsuarioRepository usuarios;
	private final ComunaActual comunaActual;

	// Deja la primera fila del historial de un reclamo recién creado: RECIBIDO, sin estado anterior, con el vecino
	// que lo reportó. La llama el alta, dentro de su propia transacción.
	public void registrarAlta(Reclamo reclamo, Usuario autor) {
		historial.save(new ReclamoEstado(reclamo, null, EstadoReclamo.RECIBIDO, autor));
	}

	// IMPORTANTE para quien lo use (MC-18 a MC-20): este servicio NO controla roles. El endpoint tiene que exigir el rol
	// con @PreAuthorize y pasar como `idUsuario` el id del usuario del token, nunca uno que venga del pedido.
	// Si el usuario no existe o es de otra comuna, falla (404) antes de guardar nada.
	// Cambia el estado del reclamo número `numero` de la comuna del token. `idUsuario` es quien lo cambia.
	// Bloquea la fila del reclamo: dos cambios simultáneos se hacen de a uno, y el segundo se valida contra el estado
	// que dejó el primero. Si la espera pasa los 3 segundos, responde 409 (ver ApiExceptionHandler).
	// Errores: reclamo inexistente o de otra comuna -> RecursoNoEncontradoException (404); transición no permitida ->
	// TransicionInvalidaException (409); datos faltantes o inválidos -> PeticionInvalidaException (400).
	// Lleva su propio @Transactional: la llamada a la sobrecarga de abajo es interna y no pasa por el proxy.
	@Transactional(timeout = SEGUNDOS_MAXIMOS)
	public Reclamo cambiar(Integer numero, CambioDeEstado cambio, Long idUsuario) {
		return cambiar(numero, cambio, idUsuario, null, null);
	}

	// Igual que cambiar, para el agente de campo (MC-20): después de bloquear la fila exige que el reclamo sea de este
	// agente (si no, 404 idéntico a uno inexistente, también si está DESCARTADO) y que esté en el estado `desde` (si no,
	// TransicionInvalidaException). Con soloDelAgente y desde en null no exige nada.
	@Transactional(timeout = SEGUNDOS_MAXIMOS)
	public Reclamo cambiar(Integer numero, CambioDeEstado cambio, Long idUsuario, Long soloDelAgente, EstadoReclamo desde) {
		if (cambio == null || cambio.nuevo() == null) {
			throw new PeticionInvalidaException("ESTADO_REQUERIDO", "Elegí el estado al que pasa el reclamo.");
		}
		Reclamo reclamo = reclamos.bloquearPorNumero(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		if (soloDelAgente != null && (reclamo.getAgente() == null || !soloDelAgente.equals(reclamo.getAgente().getId())
				|| reclamo.getEstado() == EstadoReclamo.DESCARTADO)) {
			throw new RecursoNoEncontradoException(NO_ENCONTRADO);
		}
		EstadoReclamo anterior = reclamo.getEstado();
		EstadoReclamo nuevo = cambio.nuevo();
		if (desde != null && anterior != desde) {
			throw new TransicionInvalidaException(anterior, nuevo);
		}
		if (!anterior.puedePasarA(nuevo)) {
			throw new TransicionInvalidaException(anterior, nuevo);
		}
		String nota = textoOpcional(cambio.nota(), OBSERVACION_MAXIMA, "La nota");
		// Si no hay usuario (cambio del sistema) la fila queda sin autor.
		Usuario quien = idUsuario == null ? null : usuarios.findByIdAndComunaId(idUsuario, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));

		// Antes de tocar nada: el agente (si corresponde) tiene que ser válido.
		Usuario agenteNuevo = validarAgente(nuevo, cambio.idAgente(), true);

		switch (nuevo) {
			case DESCARTADO -> {
				descartar(reclamo, cambio);
				// Un reclamo descartado no queda a cargo de nadie.
				reclamo.setAgente(null);
			}
			case ASIGNADO -> {
				sinDatosDeDescarte(cambio);
				reclamo.setAgente(agenteNuevo);
			}
			case RESUELTO -> {
				sinDatosDeDescarte(cambio);
				reclamo.setTrabajoRealizado(trabajoRealizado(cambio.trabajoRealizado()));
			}
			case RECIBIDO -> {
				sinDatosDeDescarte(cambio);
				// Volver a RECIBIDO quita el agente.
				reclamo.setAgente(null);
				// Reabrir un descartado: se borra todo lo del descarte.
				limpiarDescarte(reclamo);
			}
			default -> sinDatosDeDescarte(cambio);
		}
		reclamo.setEstado(nuevo);
		// El agente se guarda solo en el cambio que asigna: ahí queda "quién quedó a cargo".
		// Un descarte deja en la fila del historial un resumen de por qué (interno: el vecino no ve la observación), para que
		// si el reclamo se reabre quede el motivo.
		String observacion = nuevo == EstadoReclamo.DESCARTADO ? resumenDelDescarte(cambio) : nota;
		historial.save(new ReclamoEstado(reclamo, anterior, nuevo, quien, observacion, nuevo == EstadoReclamo.ASIGNADO ? agenteNuevo : null));
		reclamos.saveAndFlush(reclamo);
		return reclamo;
	}

	// Asigna o reasigna un reclamo a un agente (MC-19): RECIBIDO -> ASIGNADO; ASIGNADO o EN_REPARACION con otro agente ->
	// queda ASIGNADO con el agente nuevo (el nuevo todavía no empezó). Una reasignación ASIGNADO -> ASIGNADO es una fila
	// de historial válida: mismo estado, otro agente. Misma transacción y mismo bloqueo de fila que cambiar().
	// Igual que cambiar(): NO controla roles; el endpoint exige el rol y pasa idUsuario del token.
	// Errores: otra comuna o inexistente -> 404; RESUELTO o DESCARTADO -> ReclamoCerradoException (409 RECLAMO_CERRADO);
	// agente inválido (no existe, otra comuna, no es AGENTE, inactivo o sin contraseña) -> 400 AGENTE_INVALIDO; mismo
	// agente -> YaAsignadoException (409 YA_ASIGNADO); bloqueo vencido -> 409 CONFLICTO.
	@Transactional(timeout = SEGUNDOS_MAXIMOS)
	public Reclamo asignarAgente(Integer numero, Long idAgente, Long idUsuario) {
		Reclamo reclamo = reclamos.bloquearPorNumero(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		Usuario quien = idUsuario == null ? null : usuarios.findByIdAndComunaId(idUsuario, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));
		EstadoReclamo anterior = reclamo.getEstado();
		if (anterior == EstadoReclamo.RESUELTO || anterior == EstadoReclamo.DESCARTADO) {
			throw new ReclamoCerradoException("Este reclamo ya está cerrado: no se puede asignar a un agente.");
		}
		Usuario agenteNuevo = validarAgente(EstadoReclamo.ASIGNADO, idAgente, true);
		Usuario actual = reclamo.getAgente();
		if (actual != null && actual.getId().equals(agenteNuevo.getId())) {
			throw new YaAsignadoException("Este reclamo ya está asignado a " + agenteNuevo.getNombre() + ".");
		}
		reclamo.setAgente(agenteNuevo);
		reclamo.setEstado(EstadoReclamo.ASIGNADO);
		historial.save(new ReclamoEstado(reclamo, anterior, EstadoReclamo.ASIGNADO, quien, null, agenteNuevo));
		reclamos.saveAndFlush(reclamo);
		return reclamo;
	}

	// "Duplicado del n.º N" (más ": <observación>" si la hay) o "<Motivo legible>: <observación>". Ya validado por
	// validarDescarte. Se recorta a lo que admite la columna.
	public static String resumenDelDescarte(CambioDeEstado cambio) {
		String observacion = textoOpcional(cambio.observacion(), OBSERVACION_DESCARTE_MAXIMA, "La observación");
		String motivo = switch (cambio.motivo()) {
			case DUPLICADO -> "Duplicado del n.º " + cambio.numeroOriginal();
			case NO_ES_DE_LA_COMUNA -> "No es de la comuna";
			case NO_SE_PUEDE_ATENDER -> "No se puede atender";
			case OTRO -> "Otro motivo";
		};
		return recortarParaElHistorial(observacion == null ? motivo : motivo + ": " + observacion);
	}

	// La columna observacion del historial admite 500 caracteres: si el texto es más largo se corta con puntos suspensivos.
	// Un texto vacío o en blanco queda en null.
	public static String recortarParaElHistorial(String texto) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String limpio = texto.strip();
		return limpio.length() <= OBSERVACION_MAXIMA ? limpio : limpio.substring(0, OBSERVACION_MAXIMA - 1) + "…";
	}

	private void descartar(Reclamo reclamo, CambioDeEstado cambio) {
		String observacion = validarDescarte(cambio);
		Reclamo original = null;
		if (cambio.motivo() == MotivoDescarte.DUPLICADO) {
			original = originalValido(reclamo, cambio.numeroOriginal());
		}
		reclamo.setMotivoDescarte(cambio.motivo());
		reclamo.setObservacionDescarte(observacion);
		reclamo.setReclamoOriginal(original);
	}

	// Revisa los datos de un descarte sin mirar la base (las reglas que valen para cualquier reclamo) y devuelve la
	// observación ya recortada. Lo usan este servicio y los descartes en lote, que lo hacen una vez antes de empezar.
	//  - motivo obligatorio;
	//  - DUPLICADO exige el número del original; con otro motivo no se acepta un original;
	//  - la observación es obligatoria salvo con DUPLICADO, y de hasta 300 caracteres (el vecino la lee).
	public static String validarDescarte(CambioDeEstado cambio) {
		MotivoDescarte motivo = cambio.motivo();
		if (motivo == null) {
			throw new PeticionInvalidaException("MOTIVO_REQUERIDO", "Elegí el motivo del descarte.");
		}
		if (motivo == MotivoDescarte.DUPLICADO) {
			if (cambio.numeroOriginal() == null) {
				throw new PeticionInvalidaException("ORIGINAL_REQUERIDO",
						"Indicá el número del reclamo original del que es duplicado.");
			}
		} else if (cambio.numeroOriginal() != null) {
			throw new PeticionInvalidaException("ORIGINAL_INVALIDO",
					"Solo un reclamo duplicado se vincula con un reclamo original.");
		}
		String observacion = textoOpcional(cambio.observacion(), OBSERVACION_DESCARTE_MAXIMA, "La observación");
		if (observacion == null && motivo != MotivoDescarte.DUPLICADO) {
			throw new PeticionInvalidaException("OBSERVACION_REQUERIDA", "Escribí una observación para el vecino.");
		}
		return observacion;
	}

	// ASIGNADO exige un agente: un usuario activo con rol AGENTE de la comuna del token. Con otro estado no se acepta
	// ningún agente. Un solo código (AGENTE_INVALIDO) para cualquier problema: no existe, es de otra comuna, no es
	// agente o está inactivo.
	public Usuario validarAgente(EstadoReclamo nuevo, Long idAgente) {
		return validarAgente(nuevo, idAgente, false);
	}

	// Con bloquear=true (dentro de una transacción, después de bloquear el reclamo: siempre en el orden reclamo -> agente)
	// la fila del agente queda bloqueada y rol, estado activo y clave se validan sobre esa fila: así no se puede desactivar
	// o cambiar de rol al agente entre la validación y el guardado. Sin bloquear solo sirve para revisar antes de empezar.
	private Usuario validarAgente(EstadoReclamo nuevo, Long idAgente, boolean bloquear) {
		if (nuevo != EstadoReclamo.ASIGNADO) {
			if (idAgente != null) {
				throw new PeticionInvalidaException("AGENTE_INVALIDO", "Solo se elige un agente al asignar el reclamo.");
			}
			return null;
		}
		if (idAgente == null) {
			throw new PeticionInvalidaException("AGENTE_INVALIDO", "Elegí el agente al que se asigna el reclamo.");
		}
		return (bloquear ? usuarios.bloquearPorIdYComuna(idAgente, comunaActual.id())
				: usuarios.findByIdAndComunaId(idAgente, comunaActual.id()))
				.filter(u -> u.getRol() == Rol.AGENTE && u.isActivo() && u.getClaveHash() != null)
				.orElseThrow(() -> new PeticionInvalidaException("AGENTE_INVALIDO",
						"Elegí un agente activo de la comuna que ya haya ingresado."));
	}

	// El original: tiene que existir en la misma comuna, ser otro reclamo y no estar descartado (si no, el vecino
	// quedaría apuntando a algo que tampoco se está atendiendo). Un solo mensaje para cualquier motivo.
	private Reclamo originalValido(Reclamo reclamo, Integer numeroOriginal) {
		if (numeroOriginal == null) {
			throw new PeticionInvalidaException("ORIGINAL_REQUERIDO",
					"Indicá el número del reclamo original del que es duplicado.");
		}
		Reclamo original = reclamos.findByComunaIdAndNumero(reclamo.getComuna().getId(), numeroOriginal)
				.filter(o -> !o.getId().equals(reclamo.getId()) && o.getEstado() != EstadoReclamo.DESCARTADO)
				.orElseThrow(() -> new PeticionInvalidaException("ORIGINAL_INVALIDO",
						"El reclamo original tiene que existir en la comuna, ser otro reclamo y no estar descartado."));
		return original;
	}

	private static void limpiarDescarte(Reclamo reclamo) {
		reclamo.setMotivoDescarte(null);
		reclamo.setObservacionDescarte(null);
		reclamo.setReclamoOriginal(null);
	}

	// El motivo y el original solo tienen sentido al descartar: si llegan en otro cambio es un error de quien llama.
	private static void sinDatosDeDescarte(CambioDeEstado cambio) {
		if (cambio.motivo() != null || cambio.numeroOriginal() != null || cambio.observacion() != null) {
			throw new PeticionInvalidaException("DATOS_DE_DESCARTE_SOBRANTES",
					"El motivo y el reclamo original solo se informan al descartar.");
		}
	}


	private static String trabajoRealizado(String texto) {
		return textoOpcional(texto, TRABAJO_MAXIMO, "Lo realizado");
	}

	// Un texto vacío o solo con espacios cuenta como "sin texto" (la base no admite cadenas vacías). Se recorta.
	private static String textoOpcional(String texto, int maximo, String cual) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		String limpio = texto.strip();
		if (limpio.length() > maximo) {
			throw new PeticionInvalidaException("TEXTO_MUY_LARGO", cual + " puede tener hasta " + maximo + " caracteres.");
		}
		return limpio;
	}
}
