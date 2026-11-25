package com.micomuna.services;

import java.time.Clock;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.EstadoSugerenciaIa;
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

import lombok.RequiredArgsConstructor;

// Clasificación del reclamo por el personal (MC-25): fija el tipo y deja registrada la decisión sobre la sugerencia de la IA.
// La IA solo sugiere: acá decide una persona. No cambia el estado ni manda correos.
// Igual que CambioDeEstadoService, NO controla el rol (lo exige el endpoint con @PreAuthorize) pero sí que quien decide sea
// personal de la comuna (V13 no puede mirar el rol). La comuna sale de ComunaActual; el reclamo se bloquea (SELECT ... FOR UPDATE
// WAIT 3): dos cambios sobre el mismo reclamo se hacen de a uno y, si la espera vence, responde 409 CONFLICTO.
// La IA corre en segundo plano y solo AGREGA filas de sugerencia (nunca toca el reclamo ni las filas existentes), así que no
// compite con esto: una sugerencia que llegue después del cambio queda pendiente de decisión.
@Service
@RequiredArgsConstructor
public class CambioDeTipoService {

	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";

	private final ReclamoRepository reclamos;
	private final ReclamoEstadoRepository historial;
	private final SugerenciaIaRepository sugerencias;
	private final TipoReclamoRepository tipos;
	private final UsuarioRepository usuarios;
	private final ComunaActual comunaActual;
	private final Clock reloj;

	// Errores: reclamo de otra comuna o inexistente -> 404; tipo que no existe -> 400 TIPO_INVALIDO; RESUELTO o DESCARTADO ->
	// 409 RECLAMO_CERRADO; mismo tipo sin sugerencia pendiente -> 409 MISMO_TIPO.
	@Transactional(timeout = CambioDeEstadoService.SEGUNDOS_MAXIMOS)
	public void cambiarTipo(Integer numero, String codigoDeTipo, Long idUsuario) {
		Reclamo reclamo = reclamos.bloquearPorNumero(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		Usuario quien = usuarios.findByIdAndComunaId(idUsuario, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));
		if (quien.getRol() != Rol.ADMINISTRATIVO && quien.getRol() != Rol.ADMIN_COMUNA) {
			throw new AccessDeniedException("Solo el personal administrativo decide el tipo.");
		}
		String buscado = codigoDeTipo == null ? "" : codigoDeTipo.strip();
		TipoReclamo nuevo = tipos.findByCodigo(buscado).orElseThrow(
				() -> new PeticionInvalidaException("TIPO_INVALIDO", "Ese tipo de problema no existe."));
		if (reclamo.getEstado() == EstadoReclamo.RESUELTO || reclamo.getEstado() == EstadoReclamo.DESCARTADO) {
			throw new ReclamoCerradoException("Este reclamo ya está cerrado: no se puede cambiar el tipo.");
		}

		TipoReclamo actual = reclamo.getTipo();
		boolean cambia = !actual.getCodigo().equals(nuevo.getCodigo());
		// La sugerencia vigente es la fila más nueva; solo una HECHA se puede decidir.
		SugerenciaIa vigente = sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(reclamo.getId())
				.filter(s -> s.getEstado() == EstadoSugerenciaIa.HECHA).orElse(null);
		if (!cambia && (vigente == null || vigente.getDecision() != null)) {
			throw new MismoTipoException("El reclamo ya tiene ese tipo.");
		}

		if (vigente != null) {
			vigente.decidir(nuevo.getCodigo(), quien.getId(), reloj.instant());
		}
		if (cambia) {
			reclamo.setTipo(nuevo);
			// Constancia en el historial: mismo estado, con la observación. El vecino no la ve (su historial solo muestra
			// cambios de estado), y no es una reasignación porque no trae agente.
			historial.save(new ReclamoEstado(reclamo, reclamo.getEstado(), reclamo.getEstado(), quien,
					"Cambió el tipo de " + actual.getNombre() + " a " + nuevo.getNombre()));
			reclamos.saveAndFlush(reclamo);
		}
	}
}
