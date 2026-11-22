package com.micomuna.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
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
	@Transactional(timeout = SEGUNDOS_MAXIMOS)
	public Reclamo cambiar(Integer numero, CambioDeEstado cambio, Long idUsuario) {
		if (cambio == null || cambio.nuevo() == null) {
			throw new PeticionInvalidaException("ESTADO_REQUERIDO", "Elegí el estado al que pasa el reclamo.");
		}
		Reclamo reclamo = reclamos.bloquearPorNumero(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		EstadoReclamo anterior = reclamo.getEstado();
		EstadoReclamo nuevo = cambio.nuevo();
		if (!anterior.puedePasarA(nuevo)) {
			throw new TransicionInvalidaException(anterior, nuevo);
		}
		String nota = textoOpcional(cambio.nota(), OBSERVACION_MAXIMA, "La nota");
		// Si no hay usuario (cambio del sistema) la fila queda sin autor.
		Usuario quien = idUsuario == null ? null : usuarios.findByIdAndComunaId(idUsuario, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));

		switch (nuevo) {
			case DESCARTADO -> descartar(reclamo, cambio);
			case RESUELTO -> {
				sinDatosDeDescarte(cambio);
				reclamo.setTrabajoRealizado(trabajoRealizado(cambio.trabajoRealizado()));
			}
			case RECIBIDO -> {
				sinDatosDeDescarte(cambio);
				// Reabrir un descartado: se borra todo lo del descarte.
				limpiarDescarte(reclamo);
			}
			default -> sinDatosDeDescarte(cambio);
		}
		reclamo.setEstado(nuevo);
		historial.save(new ReclamoEstado(reclamo, anterior, nuevo, quien, nota));
		reclamos.saveAndFlush(reclamo);
		return reclamo;
	}

	private void descartar(Reclamo reclamo, CambioDeEstado cambio) {
		MotivoDescarte motivo = cambio.motivo();
		if (motivo == null) {
			throw new PeticionInvalidaException("MOTIVO_REQUERIDO", "Elegí el motivo del descarte.");
		}
		String observacion = textoOpcional(cambio.observacion(), OBSERVACION_MAXIMA, "La observación");
		Reclamo original = null;
		if (motivo == MotivoDescarte.DUPLICADO) {
			original = originalValido(reclamo, cambio.numeroOriginal());
		} else if (cambio.numeroOriginal() != null) {
			throw new PeticionInvalidaException("ORIGINAL_INVALIDO",
					"Solo un reclamo duplicado se vincula con un reclamo original.");
		}
		reclamo.setMotivoDescarte(motivo);
		reclamo.setObservacionDescarte(observacion);
		reclamo.setReclamoOriginal(original);
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
