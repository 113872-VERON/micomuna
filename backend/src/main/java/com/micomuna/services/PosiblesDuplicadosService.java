package com.micomuna.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.dtos.PosibleDuplicadoDto;
import com.micomuna.dtos.PosiblesDuplicadosDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.ReclamoCerradoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.exceptions.RevisionDeDuplicadosException;
import com.micomuna.models.CandidatoCercano;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;

import lombok.RequiredArgsConstructor;

// Aviso de posibles duplicados cercanos (MC-26). Es una regla del backend, sin IA: no se guarda la lista, se calcula cada vez
// que el personal pide el detalle. La comuna sale siempre de ComunaActual. El rol lo exige el controlador; acá solo se
// controla que quien marca sea personal de la comuna (la base no puede mirar el rol).
@Service
@RequiredArgsConstructor
public class PosiblesDuplicadosService {

	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";
	public static final int RADIO_METROS = 100;
	public static final int MAXIMO = 5;
	static final double RADIO_TERRESTRE_METROS = 6_371_000d;
	// La caja es un poco más grande que el radio: solo descarta, la distancia exacta decide.
	private static final double MARGEN_DE_LA_CAJA = 1.05;
	private static final int ESCALA = 6;

	private final ReclamoRepository reclamos;
	private final UsuarioRepository usuarios;
	private final ComunaActual comunaActual;
	private final Clock reloj;

	// El aviso del detalle. Candidatos vacíos si el reclamo está cerrado, no tiene ubicación o ya se marcó como nuevo.
	@Transactional(readOnly = true)
	public PosiblesDuplicadosDto de(Reclamo reclamo) {
		boolean marcado = reclamo.getNoDuplicadoEn() != null;
		String quien = marcado && reclamo.getUsuarioNoDuplicado() != null ? reclamo.getUsuarioNoDuplicado().getNombre() : null;
		List<PosibleDuplicadoDto> candidatos = marcado ? List.of() : candidatos(reclamo);
		return new PosiblesDuplicadosDto(candidatos, quien, reclamo.getNoDuplicadoEn());
	}

	// Registra que el personal decidió que el reclamo es nuevo. Se bloquea la fila (como en el cambio de tipo): dos personas a la
	// vez se hacen de a una y, si la espera vence, responde 409 CONFLICTO. No cambia estado ni historial ni actualizado_en,
	// y no manda correos.
	// Errores: otra comuna o inexistente -> 404; cerrado -> 409 RECLAMO_CERRADO; ya marcado -> 409 YA_MARCADO; sin candidatos
	// ahora -> 409 SIN_CANDIDATOS.
	@Transactional(timeout = CambioDeEstadoService.SEGUNDOS_MAXIMOS)
	public void marcarNoEsDuplicado(Integer numero, Long idUsuario) {
		Reclamo reclamo = reclamos.bloquearPorNumero(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		Usuario quien = usuarios.findByIdAndComunaId(idUsuario, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));
		if (quien.getRol() != Rol.ADMINISTRATIVO && quien.getRol() != Rol.ADMIN_COMUNA) {
			throw new AccessDeniedException("Solo el personal administrativo revisa los duplicados.");
		}
		if (estaCerrado(reclamo)) {
			throw new ReclamoCerradoException("Este reclamo ya está cerrado: no hace falta revisar duplicados.");
		}
		if (reclamo.getNoDuplicadoEn() != null) {
			throw new RevisionDeDuplicadosException("YA_MARCADO", "Este reclamo ya se marcó como nuevo.");
		}
		if (candidatos(reclamo).isEmpty()) {
			throw new RevisionDeDuplicadosException("SIN_CANDIDATOS", "Este reclamo no tiene posibles duplicados cerca.");
		}
		// Actualización directa: no pasa por @PreUpdate, así actualizado_en no se mueve.
		reclamos.marcarNoDuplicado(reclamo.getId(), quien.getId(), reloj.instant());
	}

	private static boolean estaCerrado(Reclamo r) {
		return r.getEstado() == EstadoReclamo.RESUELTO || r.getEstado() == EstadoReclamo.DESCARTADO;
	}

	private List<PosibleDuplicadoDto> candidatos(Reclamo r) {
		if (estaCerrado(r) || r.getLatitud() == null || r.getLongitud() == null) {
			return List.of();
		}
		double lat = r.getLatitud().doubleValue();
		double lng = r.getLongitud().doubleValue();
		// Un grado de latitud son ~111,2 km; un grado de longitud se achica con el coseno de la latitud.
		double dLat = Math.toDegrees(RADIO_METROS * MARGEN_DE_LA_CAJA / RADIO_TERRESTRE_METROS);
		double dLng = dLat / Math.max(Math.cos(Math.toRadians(lat)), 1e-6);
		List<CandidatoCercano> enLaCaja = reclamos.candidatosCercanos(r.getComuna().getId(), r.getTipo().getId(), r.getId(),
				grados(lat - dLat), grados(lat + dLat), grados(lng - dLng), grados(lng + dLng));

		return enLaCaja.stream()
				.map(c -> new Medido(c, haversine(lat, lng, c.latitud().doubleValue(), c.longitud().doubleValue())))
				.filter(m -> m.metros() <= RADIO_METROS)
				// Del más cercano al más lejano; a igual distancia (al metro), el más viejo primero.
				.sorted(Comparator.comparingLong(Medido::redondeado).thenComparing(m -> m.c().creadoEn())
						.thenComparing(m -> m.c().id()))
				.limit(MAXIMO)
				.map(m -> new PosibleDuplicadoDto(m.c().numero(), m.c().estado(), (int) m.redondeado(), m.c().creadoEn(),
						m.c().direccion(), m.c().descripcion()))
				.toList();
	}

	private static BigDecimal grados(double valor) {
		return BigDecimal.valueOf(valor).setScale(ESCALA, RoundingMode.HALF_UP);
	}

	// Distancia sobre la esfera, en metros.
	static double haversine(double lat1, double lng1, double lat2, double lng2) {
		double f1 = Math.toRadians(lat1);
		double f2 = Math.toRadians(lat2);
		double df = f2 - f1;
		double dl = Math.toRadians(lng2 - lng1);
		double a = Math.sin(df / 2) * Math.sin(df / 2) + Math.cos(f1) * Math.cos(f2) * Math.sin(dl / 2) * Math.sin(dl / 2);
		return 2 * RADIO_TERRESTRE_METROS * Math.asin(Math.min(1, Math.sqrt(a)));
	}

	private record Medido(CandidatoCercano c, double metros) {
		long redondeado() {
			return Math.round(metros);
		}
	}
}
