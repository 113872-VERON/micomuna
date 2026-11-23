package com.micomuna.services;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.dtos.CambioDeEstadoDto;
import com.micomuna.dtos.DescarteDto;
import com.micomuna.dtos.FotoReclamoDto;
import com.micomuna.dtos.MiReclamoDetalleDto;
import com.micomuna.dtos.MiReclamoResumenDto;
import com.micomuna.dtos.PaginaMisReclamosDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

// "Mis reclamos" del vecino (MC-17). La comuna sale siempre del token (ComunaActual) y el dueño es el usuario
// autenticado: ninguno de los dos se toma del pedido. Un reclamo ajeno, de otra comuna o inexistente responde
// exactamente lo mismo (404), para no dar pistas de qué números existen.
@Service
@RequiredArgsConstructor
public class MisReclamosService {

	private static final Logger log = LoggerFactory.getLogger(MisReclamosService.class);

	public static final int TAMANIO_POR_DEFECTO = 20;
	public static final int TAMANIO_MAXIMO = 50;
	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";
	// Lo único que se sirve como imagen: lo que guarda ImagenUtil.limpiar. Cualquier otra cosa no se devuelve.
	private static final Set<String> TIPOS_DE_IMAGEN = Set.of("image/jpeg", "image/png");

	private final ReclamoRepository reclamos;
	private final ReclamoFotoRepository fotos;
	private final ReclamoEstadoRepository historial;
	private final ComunaActual comunaActual;

	// Una imagen lista para servir: los bytes y el tipo que guardó el servidor.
	public record Imagen(byte[] bytes, String tipo) {
	}

	@Transactional(readOnly = true)
	public PaginaMisReclamosDto listar(int pagina, int tamanio, UsuarioAutenticado vecino) {
		Page<MiReclamoResumenDto> resultado = reclamos.misReclamos(comunaActual.id(), vecino.id(),
				PageRequest.of(pagina, tamanio));
		return new PaginaMisReclamosDto(resultado.getContent(), pagina, tamanio, resultado.getTotalElements());
	}

	@Transactional(readOnly = true)
	public MiReclamoDetalleDto detalle(Integer numero, UsuarioAutenticado vecino) {
		Reclamo reclamo = reclamos.buscarDelVecino(comunaActual.id(), numero, vecino.id())
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));

		// Primero las fotos del vecino y después las del trabajo, cada grupo por su orden (la consulta ya viene por orden
		// y el sort es estable). OrigenFoto declara VECINO antes que TRABAJO.
		List<FotoReclamoDto> datosDeFotos = fotos.datosDe(reclamo.getId()).stream()
				.filter(f -> f.origen() == OrigenFoto.VECINO || reclamo.getEstado() == EstadoReclamo.RESUELTO)
				.sorted(Comparator.comparing(FotoReclamoDto::origen)).toList();
		List<CambioDeEstadoDto> cambios = historial.historialDe(reclamo.getId());

		DescarteDto descarte = reclamo.getEstado() == EstadoReclamo.DESCARTADO ? descarteDe(reclamo) : null;
		String trabajo = reclamo.getEstado() == EstadoReclamo.RESUELTO ? reclamo.getTrabajoRealizado() : null;

		return new MiReclamoDetalleDto(reclamo.getNumero(), reclamo.getTipo().getCodigo(), reclamo.getTipo().getNombre(),
				reclamo.getEstado(), reclamo.getDescripcion(), reclamo.getReferencia(), reclamo.getDireccion(),
				reclamo.getLatitud() == null ? null : reclamo.getLatitud().doubleValue(),
				reclamo.getLongitud() == null ? null : reclamo.getLongitud().doubleValue(), reclamo.getCreadoEn(),
				datosDeFotos, cambios, descarte, trabajo);
	}

	// Del original solo se muestran su estado y sus cambios de estado (estado y fecha, sin reasignaciones, igual que el
	// historial del vecino): nunca su número, su descripción, su vecino ni quién lo cambió.
	// Con DUPLICADO el vecino no recibe ninguna observación (al descartar no se acepta; esto cubre las que pudieran haber quedado
	// de antes): al vecino de un duplicado se le dice siempre lo mismo.
	private static String observacionParaElVecino(Reclamo reclamo) {
		return reclamo.getMotivoDescarte() == com.micomuna.enums.MotivoDescarte.DUPLICADO ? null : reclamo.getObservacionDescarte();
	}

	private DescarteDto descarteDe(Reclamo reclamo) {
		Reclamo original = reclamo.getReclamoOriginal();
		if (original == null) {
			return new DescarteDto(reclamo.getMotivoDescarte(), observacionParaElVecino(reclamo), null, List.of());
		}
		return new DescarteDto(reclamo.getMotivoDescarte(), observacionParaElVecino(reclamo), original.getEstado(),
				historial.historialDe(original.getId()));
	}

	// La foto tiene que ser de un reclamo del vecino, en su comuna (una sola consulta). El tipo es el que guardó el
	// servidor al limpiar la imagen; si por algún motivo no fuera uno de los permitidos, no se sirve.
	@Transactional(readOnly = true)
	public Imagen foto(Integer numero, Long idFoto, UsuarioAutenticado vecino) {
		ReclamoFoto foto = fotos.buscarDelVecino(idFoto, numero, comunaActual.id(), vecino.id())
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		return paraServir(foto);
	}

	// Solo se sirve lo que guarda ImagenUtil.limpiar (image/jpeg o image/png); cualquier otro tipo, aunque estuviera
	// guardado, responde 404. También lo usa el panel del personal.
	static Imagen paraServir(ReclamoFoto foto) {
		if (!TIPOS_DE_IMAGEN.contains(foto.getImagenTipo())) {
			log.warn("La foto {} tiene un tipo que no se sirve: {}", foto.getId(), foto.getImagenTipo());
			throw new RecursoNoEncontradoException(NO_ENCONTRADO);
		}
		return new Imagen(foto.getImagen(), foto.getImagenTipo());
	}
}
