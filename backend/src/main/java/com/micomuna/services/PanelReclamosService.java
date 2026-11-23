package com.micomuna.services;

import java.text.Collator;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.dtos.AgenteDto;
import com.micomuna.dtos.AgenteConCargaDto;
import com.micomuna.dtos.CambioDeEstadoPanelDto;
import com.micomuna.dtos.DescartePanelDto;
import com.micomuna.dtos.FotoReclamoDto;
import com.micomuna.dtos.MapaReclamosDto;
import com.micomuna.dtos.PaginaReclamosPanelDto;
import com.micomuna.dtos.PuntoReclamoDto;
import com.micomuna.dtos.ReclamoPanelDetalleDto;
import com.micomuna.dtos.ReclamoPanelDto;
import com.micomuna.dtos.ResumenPanelDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.models.FiltrosDelPanel;
import com.micomuna.repositories.PanelReclamosRepository;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;

import lombok.RequiredArgsConstructor;

// Panel de reclamos del personal (MC-18): lista, mapa, resumen, detalle, fotos y agentes. La comuna sale siempre del
// token (ComunaActual). Un reclamo de otra comuna es igual que uno inexistente (404). El rol lo exige el controlador.
@Service
@RequiredArgsConstructor
public class PanelReclamosService {

	public static final int TEXTO_MINIMO = 2;
	public static final int TEXTO_MAXIMO = 100;
	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";

	private final PanelReclamosRepository panel;
	private final ReclamoEstadoRepository historial;
	private final ReclamoFotoRepository fotos;
	private final TipoReclamoRepository tipos;
	private final UsuarioRepository usuarios;
	private final ComunaActual comunaActual;

	// Interpreta los filtros. Un tipo que no existe o un texto de menos de 2 o más de 100 caracteres es un 400
	// (VALIDACION). Un agente de otra comuna no da error: simplemente no encuentra nada.
	// "buscar": solo dígitos = ese número de reclamo; si no, texto en dirección y referencia.
	@Transactional(readOnly = true)
	public FiltrosDelPanel filtros(EstadoReclamo estado, String tipo, Long agente, String buscar) {
		String codigoDeTipo = null;
		if (tipo != null && !tipo.isBlank()) {
			codigoDeTipo = tipos.findByCodigo(tipo.strip())
					.orElseThrow(() -> new PeticionInvalidaException("VALIDACION", "Ese tipo de problema no existe."))
					.getCodigo();
		}
		Integer numero = null;
		String texto = null;
		boolean numeroImposible = false;
		String buscado = buscar == null ? "" : buscar.strip();
		if (!buscado.isEmpty()) {
			if (buscado.chars().allMatch(c -> c >= '0' && c <= '9')) {
				try {
					numero = Integer.valueOf(buscado);
				} catch (NumberFormatException e) {
					numeroImposible = true;
				}
			} else if (buscado.length() < TEXTO_MINIMO || buscado.length() > TEXTO_MAXIMO) {
				throw new PeticionInvalidaException("VALIDACION",
						"Buscá con un número o con un texto de " + TEXTO_MINIMO + " a " + TEXTO_MAXIMO + " caracteres.");
			} else {
				texto = FiltrosDelPanel.normalizar(buscado);
			}
		}
		return new FiltrosDelPanel(estado, codigoDeTipo, agente, numero, texto, numeroImposible);
	}

	@Transactional(readOnly = true)
	public PaginaReclamosPanelDto listar(FiltrosDelPanel filtros, int pagina, int tamanio) {
		Long comuna = comunaActual.id();
		List<ReclamoPanelDto> filas = panel.listar(comuna, filtros, pagina, tamanio);
		return new PaginaReclamosPanelDto(filas, pagina, tamanio, panel.contar(comuna, filtros));
	}

	@Transactional(readOnly = true)
	public MapaReclamosDto mapa(FiltrosDelPanel filtros) {
		List<PuntoReclamoDto> puntos = panel.puntos(comunaActual.id(), filtros);
		boolean recortado = puntos.size() > PanelReclamosRepository.MAXIMO_PUNTOS_DEL_MAPA;
		return new MapaReclamosDto(
				recortado ? puntos.subList(0, PanelReclamosRepository.MAXIMO_PUNTOS_DEL_MAPA) : puntos, recortado);
	}

	// Por estado con los filtros salvo el de estado; "sin asignar" son los RECIBIDOS de la comuna, sin ningún filtro.
	@Transactional(readOnly = true)
	public ResumenPanelDto resumen(FiltrosDelPanel filtros) {
		Long comuna = comunaActual.id();
		Map<EstadoReclamo, Long> contados = panel.contarPorEstado(comuna, filtros);
		Map<EstadoReclamo, Long> porEstado = new EnumMap<>(EstadoReclamo.class);
		long total = 0;
		for (EstadoReclamo estado : EstadoReclamo.values()) {
			long cantidad = contados.getOrDefault(estado, 0L);
			porEstado.put(estado, cantidad);
			total += cantidad;
		}
		PanelReclamosRepository.SinAsignar sinAsignar = panel.sinAsignar(comuna);
		return new ResumenPanelDto(porEstado, total, sinAsignar.cantidad(), sinAsignar.masAntiguo());
	}

	@Transactional(readOnly = true)
	public ReclamoPanelDetalleDto detalle(Integer numero) {
		Reclamo reclamo = panel.detalle(comunaActual.id(), numero)
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		boolean resuelto = reclamo.getEstado() == EstadoReclamo.RESUELTO;

		// Las fotos del trabajo solo se muestran con el reclamo RESUELTO, igual que lo ve el vecino.
		List<FotoReclamoDto> datosDeFotos = fotos.datosDe(reclamo.getId()).stream()
				.filter(f -> f.origen() == OrigenFoto.VECINO || resuelto)
				.sorted(Comparator.comparing(FotoReclamoDto::origen)).toList();
		List<CambioDeEstadoPanelDto> cambios = panel.historialParaElPersonal(reclamo.getId(), reclamo.getComuna().getId());

		DescartePanelDto descarte = null;
		if (reclamo.getEstado() == EstadoReclamo.DESCARTADO) {
			Reclamo original = reclamo.getReclamoOriginal();
			descarte = new DescartePanelDto(reclamo.getMotivoDescarte(), reclamo.getObservacionDescarte(),
					original == null ? null : original.getNumero());
		}
		return new ReclamoPanelDetalleDto(reclamo.getNumero(), reclamo.getTipo().getCodigo(),
				reclamo.getTipo().getNombre(), reclamo.getEstado(), reclamo.getDescripcion(), reclamo.getReferencia(),
				reclamo.getDireccion(), reclamo.getLatitud() == null ? null : reclamo.getLatitud().doubleValue(),
				reclamo.getLongitud() == null ? null : reclamo.getLongitud().doubleValue(), reclamo.getCreadoEn(),
				reclamo.getVecino().getNombre(), AgenteDto.desde(reclamo.getAgente()), datosDeFotos, cambios, descarte,
				resuelto ? reclamo.getTrabajoRealizado() : null, panel.numerosDeDuplicados(reclamo.getId()));
	}

	@Transactional(readOnly = true)
	public MisReclamosService.Imagen foto(Integer numero, Long idFoto) {
		ReclamoFoto foto = fotos.buscarEnComuna(idFoto, numero, comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		return MisReclamosService.paraServir(foto);
	}

	// Usuarios activos con rol AGENTE de la comuna, por nombre (sin distinguir mayúsculas ni tildes).
	@Transactional(readOnly = true)
	public List<AgenteConCargaDto> agentes() {
		Collator orden = Collator.getInstance(Locale.forLanguageTag("es-AR"));
		orden.setStrength(Collator.PRIMARY);
		return panel.agentesConCarga(comunaActual.id()).stream()
				.sorted(Comparator.comparing(AgenteConCargaDto::nombre, orden).thenComparing(AgenteConCargaDto::id)).toList();
	}
}
