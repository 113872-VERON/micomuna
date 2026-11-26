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
import com.micomuna.dtos.PrioridadPanelDto;
import com.micomuna.dtos.PrioridadReclamoDto;
import com.micomuna.dtos.PuntoReclamoDto;
import com.micomuna.dtos.ReclamoPanelDetalleDto;
import com.micomuna.dtos.ReclamoPanelDto;
import com.micomuna.dtos.ResumenPanelDto;
import com.micomuna.dtos.SugerenciaIaDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.EstadoSugerenciaIa;
import com.micomuna.enums.EstadoSugerenciaPanel;
import com.micomuna.enums.OrdenPanel;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.models.FiltrosDelPanel;
import com.micomuna.repositories.PanelReclamosRepository;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.SugerenciaIaRepository;
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
	private final SugerenciaIaRepository sugerencias;
	private final UsuarioRepository usuarios;
	private final PosiblesDuplicadosService posiblesDuplicados;
	private final PrioridadDelReclamo prioridades;
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
	public PaginaReclamosPanelDto listar(FiltrosDelPanel filtros, String orden, int pagina, int tamanio) {
		OrdenPanel ordenInterpretado = OrdenPanel.interpretar(orden);
		if (ordenInterpretado == null) {
			throw new PeticionInvalidaException("VALIDACION", "El orden tiene que ser fecha o prioridad.");
		}
		Long comuna = comunaActual.id();
		List<ReclamoPanelDto> filas = panel.listar(comuna, filtros, ordenInterpretado, pagina, tamanio);
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
				resuelto ? reclamo.getTrabajoRealizado() : null, panel.numerosDeDuplicados(reclamo.getId()), sugerenciaIa(reclamo),
				posiblesDuplicados.de(reclamo), prioridad(reclamo));
	}


	// La sugerencia vigente de la IA (la fila más nueva): sin filas SIN_ANALIZAR; ERROR u OMITIDA NO_DISPONIBLE (sin tipo ni
	// justificación, y nunca el detalle del error, el modelo ni los tokens); HECHA con el tipo, la justificación y, si el
	// personal ya decidió, la decisión. La prioridad sugerida viaja solo con HECHA (MC-27).
	private SugerenciaIaDto sugerenciaIa(Reclamo reclamo) {
		SugerenciaIa s = sugerencias.findFirstByIdReclamoOrderByCreadaEnDescIdDesc(reclamo.getId()).orElse(null);
		if (s == null) {
			return SugerenciaIaDto.sinAnalizar();
		}
		if (s.getEstado() != EstadoSugerenciaIa.HECHA) {
			return SugerenciaIaDto.noDisponible();
		}
		String decididaPor = s.getIdUsuarioDecision() == null ? null
				: usuarios.findByIdAndComunaId(s.getIdUsuarioDecision(), reclamo.getComuna().getId()).map(Usuario::getNombre)
						.orElse(null);
		return new SugerenciaIaDto(EstadoSugerenciaPanel.HECHA, s.getTipoSugerido(), nombreDeTipo(s.getTipoSugerido()),
				s.getJustificacion(), s.getTipoSugerido().equals(reclamo.getTipo().getCodigo()), s.getDecision(),
				s.getTipoDecidido(), nombreDeTipo(s.getTipoDecidido()), decididaPor, s.getDecididaEn(), s.getPrioridadSugerida());
	}

	private String nombreDeTipo(String codigo) {
		return codigo == null ? null : tipos.findByCodigo(codigo).map(t -> t.getNombre()).orElse(null);
	}

	// La prioridad efectiva y, si la fijó el personal, quién y cuándo (el nombre sale de la comuna del reclamo).
	private PrioridadPanelDto prioridad(Reclamo reclamo) {
		PrioridadReclamoDto efectiva = prioridades.de(reclamo);
		if (reclamo.getPrioridad() == null) {
			return PrioridadPanelDto.de(efectiva, null, null);
		}
		Usuario quien = reclamo.getUsuarioPrioridad();
		return PrioridadPanelDto.de(efectiva, quien == null ? null : quien.getNombre(), reclamo.getPrioridadFijadaEn());
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
