package com.micomuna.services;

import java.time.Clock;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.MismaPrioridadException;
import com.micomuna.exceptions.ReclamoCerradoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;

import lombok.RequiredArgsConstructor;

// Prioridad del reclamo fijada por el personal (MC-27). La IA solo sugiere: acá decide una persona, y lo único que se guarda en
// el reclamo es esa decisión (con quién y cuándo). No cambia el estado, no manda correos y no mueve actualizado_en.
// Igual que CambioDeTipoService, NO controla el rol (lo exige el endpoint con @PreAuthorize) pero sí que quien decide sea
// personal de la comuna (la base no puede mirar el rol). La comuna sale de ComunaActual; el reclamo se bloquea (SELECT ... FOR
// UPDATE WAIT 3): dos cambios sobre el mismo reclamo se hacen de a uno y, si la espera vence, responde 409 CONFLICTO.
@Service
@RequiredArgsConstructor
public class CambioDePrioridadService {

	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";

	private final ReclamoRepository reclamos;
	private final ReclamoEstadoRepository historial;
	private final UsuarioRepository usuarios;
	private final PrioridadDelReclamo prioridades;
	private final ComunaActual comunaActual;
	private final Clock reloj;

	// Errores: otra comuna o inexistente -> 404; RESUELTO o DESCARTADO -> 409 RECLAMO_CERRADO; la misma que ya fijó el personal
	// -> 409 MISMA_PRIORIDAD.
	// Si la prioridad efectiva cambia, queda una fila en el historial con el estado actual en estado_anterior y estado (como el
	// cambio de tipo de MC-25). Si no cambia (se confirma la sugerida por la IA), solo queda quién y cuándo.
	@Transactional(timeout = CambioDeEstadoService.SEGUNDOS_MAXIMOS)
	public void cambiarPrioridad(Integer numero, PrioridadIa nueva, Long idUsuario) {
		Reclamo reclamo = reclamos.bloquearPorNumero(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		Usuario quien = usuarios.findByIdAndComunaId(idUsuario, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));
		if (quien.getRol() != Rol.ADMINISTRATIVO && quien.getRol() != Rol.ADMIN_COMUNA) {
			throw new AccessDeniedException("Solo el personal administrativo decide la prioridad.");
		}
		if (reclamo.getEstado() == EstadoReclamo.RESUELTO || reclamo.getEstado() == EstadoReclamo.DESCARTADO) {
			throw new ReclamoCerradoException("Este reclamo ya está cerrado: no se puede cambiar la prioridad.");
		}
		if (reclamo.getPrioridad() == nueva) {
			throw new MismaPrioridadException("El reclamo ya tiene esa prioridad.");
		}

		PrioridadIa antes = reclamo.getPrioridad() != null ? reclamo.getPrioridad() : prioridades.sugeridaPorLaIa(reclamo);
		if (antes != nueva) {
			// Constancia en el historial: mismo estado, con la observación. El vecino no la ve (su historial solo muestra
			// cambios de estado) y no es una reasignación porque no trae agente.
			historial.save(new ReclamoEstado(reclamo, reclamo.getEstado(), reclamo.getEstado(), quien,
					"Cambió la prioridad de " + nombre(antes) + " a " + nombre(nueva)));
		}
		// Actualización directa: no pasa por @PreUpdate, así actualizado_en no se mueve (igual que "no es duplicado").
		reclamos.fijarPrioridad(reclamo.getId(), nueva, quien.getId(), reloj.instant());
	}

	static String nombre(PrioridadIa prioridad) {
		if (prioridad == null) {
			return "Sin prioridad";
		}
		return switch (prioridad) {
			case ALTA -> "Alta";
			case MEDIA -> "Media";
			case BAJA -> "Baja";
		};
	}
}
