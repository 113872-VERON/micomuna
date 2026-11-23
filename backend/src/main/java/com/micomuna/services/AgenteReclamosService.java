package com.micomuna.services;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.micomuna.dtos.FotoReclamoDto;
import com.micomuna.dtos.PaginaReclamosAgenteDto;
import com.micomuna.dtos.ReclamoAgenteDetalleDto;
import com.micomuna.dtos.ReclamoAgenteDto;
import com.micomuna.dtos.ResolverReclamoForm;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.GrupoReclamosAgente;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.exceptions.TransicionInvalidaException;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

// "Mis reclamos" del agente de campo (MC-20). La comuna sale del token (ComunaActual) y el agente es el usuario del
// token: ninguno de los dos viene del pedido. Un reclamo de otro agente, de otra comuna, inexistente o DESCARTADO
// responde lo mismo (404). Empezar y resolver pasan por CambioDeEstadoService (bloqueo de fila, historial con quién lo
// hizo), que además vuelve a comprobar bajo el bloqueo que el reclamo siga siendo de este agente.
// Este servicio NO controla roles: el controlador exige AGENTE.
@Service
public class AgenteReclamosService {

	public static final int TAMANIO_POR_DEFECTO = 50;
	public static final int TAMANIO_MAXIMO = 100;
	// Tiempo máximo de la transacción de resolver (incluye hasta 3 segundos de espera del bloqueo de la fila).
	static final int SEGUNDOS_MAXIMOS_TRANSACCION = 10;
	static final String NO_ENCONTRADO = "No encontramos ese reclamo.";
	static final String OCUPADO = "Otra persona está cambiando este reclamo. Probá de nuevo en unos segundos.";

	private final ReclamoRepository reclamos;
	private final ReclamoFotoRepository fotos;
	private final CambioDeEstadoService cambios;
	private final ComunaActual comunaActual;
	private final TurnosDeFotos turnos;
	private final TransactionTemplate transaccion;

	public AgenteReclamosService(ReclamoRepository reclamos, ReclamoFotoRepository fotos, CambioDeEstadoService cambios,
			ComunaActual comunaActual, TurnosDeFotos turnos, PlatformTransactionManager transacciones) {
		this.reclamos = reclamos;
		this.fotos = fotos;
		this.cambios = cambios;
		this.comunaActual = comunaActual;
		this.turnos = turnos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setTimeout(SEGUNDOS_MAXIMOS_TRANSACCION);
	}

	@Transactional(readOnly = true)
	public PaginaReclamosAgenteDto listar(GrupoReclamosAgente grupo, int pagina, int tamanio, UsuarioAutenticado agente) {
		PageRequest pedido = PageRequest.of(pagina, tamanio);
		Page<ReclamoAgenteDto> resultado = grupo == GrupoReclamosAgente.RESUELTOS
				? reclamos.resueltosDelAgente(comunaActual.id(), agente.id(), pedido)
				: reclamos.paraHacerDelAgente(comunaActual.id(), agente.id(), pedido);
		return new PaginaReclamosAgenteDto(resultado.getContent(), pagina, tamanio, resultado.getTotalElements());
	}

	@Transactional(readOnly = true)
	public ReclamoAgenteDetalleDto detalle(Integer numero, UsuarioAutenticado agente) {
		return armarDetalle(propio(numero, agente));
	}

	// El detalle a partir de un reclamo ya cargado. Tiene que correr con la sesión abierta (tipo y fotos se leen acá).
	private ReclamoAgenteDetalleDto armarDetalle(Reclamo reclamo) {
		boolean resuelto = reclamo.getEstado() == EstadoReclamo.RESUELTO;
		// Primero las del vecino y después las del trabajo (solo con el reclamo RESUELTO, igual que las ve el vecino).
		List<FotoReclamoDto> datosDeFotos = fotos.datosDe(reclamo.getId()).stream()
				.filter(f -> f.origen() == OrigenFoto.VECINO || resuelto)
				.sorted(Comparator.comparing(FotoReclamoDto::origen)).toList();
		return new ReclamoAgenteDetalleDto(reclamo.getNumero(), reclamo.getTipo().getCodigo(), reclamo.getTipo().getNombre(),
				reclamo.getEstado(), reclamo.getDescripcion(), reclamo.getReferencia(), reclamo.getDireccion(),
				reclamo.getLatitud() == null ? null : reclamo.getLatitud().doubleValue(),
				reclamo.getLongitud() == null ? null : reclamo.getLongitud().doubleValue(), reclamo.getCreadoEn(),
				datosDeFotos, resuelto ? reclamo.getTrabajoRealizado() : null);
	}

	@Transactional(readOnly = true)
	public MisReclamosService.Imagen foto(Integer numero, Long idFoto, UsuarioAutenticado agente) {
		ReclamoFoto foto = fotos.buscarDelAgente(idFoto, numero, comunaActual.id(), agente.id())
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
		return MisReclamosService.paraServir(foto);
	}

	// ASIGNADO -> EN_REPARACION. Cualquier otro estado de partida: 409 TRANSICION_INVALIDA (también RESUELTO, que la
	// tabla general sí deja reabrir pero no es "empezar"). La comprobación de "es de este agente" y del estado la hace
	// CambioDeEstadoService con la fila bloqueada.
	// La respuesta se arma con el reclamo que devuelve el mismo cambio y dentro de la misma transacción: no se vuelve a
	// buscar "como agente". Si el personal lo reasigna justo después, el agente igual recibe el resultado de su cambio.
	public ReclamoAgenteDetalleDto empezar(Integer numero, UsuarioAutenticado agente) {
		try {
			return Objects.requireNonNull(transaccion.execute(estado -> armarDetalle(cambios.cambiar(numero,
					CambioDeEstado.a(EstadoReclamo.EN_REPARACION), agente.id(), agente.id(), EstadoReclamo.ASIGNADO))));
		} catch (TransactionTimedOutException | org.springframework.dao.QueryTimeoutException e) {
			throw new ConflictoException(OCUPADO);
		}
	}

	// EN_REPARACION -> RESUELTO, con nota y foto opcionales.
	// Orden de trabajo, pensado para no tener la fila bloqueada mientras se procesa la imagen (abrir y volver a armar una
	// foto es lo que más tarda):
	//  1. Revisión barata y sin bloqueo: que el reclamo sea de este agente y esté EN_REPARACION. Así a quien no
	//     corresponde no se le gasta un turno de foto, y no se sabe nada de reclamos ajenos (404 igual que inexistente).
	//  2. La foto se procesa con turno (TurnosDeFotos, 503 OCUPADO si el servidor está lleno), SIN transacción y sin
	//     bloqueo. Si es inválida, falla acá y no se tocó nada.
	//  3. Recién ahora una transacción corta: bloquea el reclamo, vuelve a validar dueño y estado (puede haber cambiado
	//     entre 1 y 3), cambia el estado, reemplaza las fotos del trabajo y guarda la nueva. Todo o nada.
	public ReclamoAgenteDetalleDto resolver(Integer numero, ResolverReclamoForm form, UsuarioAutenticado agente) {
		Reclamo previo = propio(numero, agente);
		if (previo.getEstado() != EstadoReclamo.EN_REPARACION) {
			throw new TransicionInvalidaException(previo.getEstado(), EstadoReclamo.RESUELTO);
		}
		ImagenUtil.Imagen limpia = prepararFoto(form.foto());
		String nota = form.nota();
		// La nota también queda en la fila del historial (observacion, interna: la ve el personal y el vecino no), para que no
		// se pierda si el reclamo se reabre y se resuelve de nuevo. Esa columna admite 500 caracteres.
		CambioDeEstado cambio = new CambioDeEstado(EstadoReclamo.RESUELTO, null, null, null, nota, notaParaElHistorial(nota));

		try {
			return Objects.requireNonNull(transaccion.execute(estado -> {
				Reclamo reclamo = cambios.cambiar(numero, cambio, agente.id(), agente.id(),
						EstadoReclamo.EN_REPARACION);
				// La resolución nueva reemplaza a la anterior: se borran siempre las fotos del trabajo viejas, haya o no foto nueva.
				fotos.eliminarFotosDelTrabajo(reclamo.getId());
				if (limpia != null) {
					fotos.save(new ReclamoFoto(reclamo, OrigenFoto.TRABAJO, 1, limpia.bytes(), limpia.tipo()));
				}
				// La respuesta sale de este mismo cambio, sin una lectura posterior filtrada por agente.
				return armarDetalle(reclamo);
			}));
		} catch (TransactionTimedOutException | org.springframework.dao.QueryTimeoutException e) {
			// Pasó el tiempo máximo de la transacción (los bloqueos vencidos ya responden 409 CONFLICTO por su cuenta).
			throw new ConflictoException(OCUPADO);
		}
	}

	// Texto de la nota para la columna observacion (hasta 500 caracteres): si es más larga se corta con puntos suspensivos;
	// completa queda en trabajo_realizado.
	static String notaParaElHistorial(String nota) {
		if (nota == null || nota.isBlank()) {
			return null;
		}
		String limpia = nota.strip();
		int maximo = CambioDeEstadoService.OBSERVACION_MAXIMA;
		return limpia.length() <= maximo ? limpia : limpia.substring(0, maximo - 1) + "…";
	}

	// Una parte vacía y sin nombre es un campo de archivo sin elegir (lo manda así el formulario del navegador): es "sin
	// foto". Una vacía con nombre, o cualquier archivo que no sea una imagen válida, es FOTO_INVALIDA (mismo control que el alta).
	private ImagenUtil.Imagen prepararFoto(MultipartFile foto) {
		if (foto == null || (foto.isEmpty() && (foto.getOriginalFilename() == null || foto.getOriginalFilename().isBlank()))) {
			return null;
		}
		return turnos.conTurno(() -> ReclamoService.prepararFoto(foto, 1));
	}

	private Reclamo propio(Integer numero, UsuarioAutenticado agente) {
		return reclamos.buscarDelAgente(comunaActual.id(), numero, agente.id())
				.orElseThrow(() -> new RecursoNoEncontradoException(NO_ENCONTRADO));
	}
}
