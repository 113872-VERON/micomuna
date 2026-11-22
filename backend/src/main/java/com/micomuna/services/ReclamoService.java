package com.micomuna.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.micomuna.dtos.CrearReclamoForm;
import com.micomuna.dtos.ReclamoCreadoDto;
import com.micomuna.dtos.TipoReclamoDto;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.LimiteSuperadoException;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoEstadoRepository;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;

// El vecino reporta un problema en SU comuna (MC-15). La comuna sale siempre del token (ComunaActual) y el vecino
// es el usuario autenticado: nada de eso se toma del pedido.
// Los mensajes de error son para el vecino: dicen qué hacer, sin palabras técnicas.
@Service
public class ReclamoService {

	public static final int MAXIMO_FOTOS = 3;
	public static final int FOTO_MAXIMO_BYTES = 2 * 1024 * 1024;
	public static final int FOTO_LADO_MAXIMO_PX = 2560;
	// Lo que puede pesar una foto ya limpia (al volver a armarla puede crecer un poco respecto de la original).
	public static final int FOTO_LIMPIA_MAXIMO_BYTES = 3 * 1024 * 1024;
	public static final int MAXIMO_RECLAMOS_POR_HORA = 10;
	// Tiempo máximo de la transacción del alta (incluye los hasta 3 segundos de espera del bloqueo de la comuna).
	static final int SEGUNDOS_MAXIMOS_TRANSACCION = 10;

	static final String UBICACION_REQUERIDA = "Tocá «Usar mi ubicación» o escribí la dirección o una referencia del lugar.";
	static final String TIPO_INVALIDO = "Elegí uno de los tipos de problema de la lista.";
	static final String DEMASIADOS_RECLAMOS = "Ya mandaste muchos reclamos en la última hora. Probá de nuevo más tarde.";
	static final String NUMERO_OCUPADO = "Hay muchos reclamos entrando en este momento. Probá de nuevo en unos segundos.";
	static final String DEMASIADAS_FOTOS = "Podés mandar hasta " + MAXIMO_FOTOS + " fotos. Sacá alguna y probá de nuevo.";

	// Seis decimales son unos 11 cm: más precisión de la que da el GPS de un celular (columnas NUMBER(8,6) y (9,6)).
	private static final int DECIMALES_COORDENADAS = 6;
	private static final Duration VENTANA_DEL_LIMITE = Duration.ofHours(1);

	private final TipoReclamoRepository tipos;
	private final ReclamoRepository reclamos;
	private final ReclamoFotoRepository fotos;
	private final ReclamoEstadoRepository historial;
	private final ComunaRepository comunas;
	private final UsuarioRepository usuarios;
	private final ComunaActual comunaActual;
	private final TurnosDeFotos turnos;
	// Propia de este servicio (no el TransactionTemplate compartido): lleva el tiempo máximo del alta.
	private final TransactionTemplate transaccion;

	public ReclamoService(TipoReclamoRepository tipos, ReclamoRepository reclamos, ReclamoFotoRepository fotos,
			ReclamoEstadoRepository historial, ComunaRepository comunas, UsuarioRepository usuarios,
			ComunaActual comunaActual, TurnosDeFotos turnos, PlatformTransactionManager transacciones) {
		this.tipos = tipos;
		this.reclamos = reclamos;
		this.fotos = fotos;
		this.historial = historial;
		this.comunas = comunas;
		this.usuarios = usuarios;
		this.comunaActual = comunaActual;
		this.turnos = turnos;
		this.transaccion = new TransactionTemplate(transacciones);
		this.transaccion.setTimeout(SEGUNDOS_MAXIMOS_TRANSACCION);
	}

	@Transactional(readOnly = true)
	public List<TipoReclamoDto> listarTipos() {
		return tipos.findAllByOrderByOrdenAsc().stream().map(TipoReclamoDto::desde).toList();
	}

	// Primero se revisa todo y se preparan las fotos, sin transacción: es lo que más tarda (abrir y volver a armar
	// hasta 3 imágenes). Recién después se abre la transacción que toma el número de la comuna, que así dura poco:
	// mientras está abierta, otro vecino de la misma comuna que reporta a la vez espera su turno.
	public ReclamoCreadoDto crear(CrearReclamoForm form, UsuarioAutenticado vecino) {
		Long comunaId = comunaActual.id();
		// Antes de procesar las fotos: a quien ya llegó al límite no se le gasta memoria ni procesador.
		verificarLimiteDelVecino(comunaId, vecino.id());
		if (!form.tieneCoordenadas() && form.referencia() == null) {
			throw new PeticionInvalidaException("UBICACION_REQUERIDA", UBICACION_REQUERIDA);
		}
		TipoReclamo tipo = tipos.findByCodigo(form.tipo())
				.orElseThrow(() -> new PeticionInvalidaException("TIPO_INVALIDO", TIPO_INVALIDO));
		List<ImagenUtil.Imagen> limpias = prepararFotos(form.fotos());

		try {
			return Objects.requireNonNull(
					transaccion.execute(estado -> guardar(form, tipo, limpias, comunaId, vecino.id())));
		} catch (PessimisticLockingFailureException | LockTimeoutException | PessimisticLockException
				| TransactionTimedOutException | org.springframework.dao.QueryTimeoutException e) {
			// La fila de la comuna estaba tomada por otra alta más de 3 segundos, o la transacción pasó los 10 segundos.
			// Mensaje propio del alta: el 409 de los otros bloqueos (MC-14) sigue con el suyo.
			throw new ConflictoException(NUMERO_OCUPADO);
		}
	}

	private void verificarLimiteDelVecino(Long comunaId, Long vecinoId) {
		Instant desde = Instant.now().minus(VENTANA_DEL_LIMITE);
		if (reclamos.contarDelVecinoDesde(comunaId, vecinoId, desde) >= MAXIMO_RECLAMOS_POR_HORA) {
			throw new LimiteSuperadoException("DEMASIADOS_RECLAMOS", DEMASIADOS_RECLAMOS);
		}
	}

	private ReclamoCreadoDto guardar(CrearReclamoForm form, TipoReclamo tipo, List<ImagenUtil.Imagen> limpias,
			Long comunaId, Long vecinoId) {
		// Bloquea la fila de la comuna hasta el final de la transacción (espera hasta 3 segundos; si se vence, 409).
		// Con la fila tomada, sumar y leer el contador da un número que nadie más puede recibir.
		Comuna comuna = comunas.bloquear(comunaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa comuna."));
		// Se vuelve a contar con la comuna bloqueada: dos altas simultáneas del mismo vecino no pasan las dos el límite.
		verificarLimiteDelVecino(comunaId, vecinoId);
		comunas.sumarNumeroReclamo(comunaId);
		Integer numero = comunas.ultimoNumeroReclamo(comunaId);
		Usuario autor = usuarios.getReferenceById(vecinoId);

		Reclamo reclamo = new Reclamo();
		reclamo.setComuna(comuna);
		reclamo.setNumero(numero);
		reclamo.setVecino(autor);
		reclamo.setTipo(tipo);
		reclamo.setDescripcion(form.descripcion());
		reclamo.setEstado(EstadoReclamo.RECIBIDO);
		reclamo.setReferencia(form.referencia());
		if (form.tieneCoordenadas()) {
			reclamo.setLatitud(redondear(form.latitud()));
			reclamo.setLongitud(redondear(form.longitud()));
		}
		reclamos.save(reclamo);

		historial.save(new ReclamoEstado(reclamo, null, EstadoReclamo.RECIBIDO, autor));
		for (int i = 0; i < limpias.size(); i++) {
			ImagenUtil.Imagen foto = limpias.get(i);
			fotos.save(new ReclamoFoto(reclamo, i + 1, foto.bytes(), foto.tipo()));
		}
		reclamos.flush();
		return ReclamoCreadoDto.desde(reclamo, limpias.size());
	}

	// Cada foto se valida por su contenido (no por el nombre ni por el tipo que declara el cliente) y se vuelve a
	// armar, para no guardar sus datos ocultos (por ejemplo la ubicación GPS de la cámara). Se procesan de a una
	// y con turno (TurnosDeFotos): así en todo el servidor hay pocas imágenes abiertas a la vez.
	private List<ImagenUtil.Imagen> prepararFotos(List<MultipartFile> archivos) {
		if (archivos.size() > MAXIMO_FOTOS) {
			throw new PeticionInvalidaException("FOTO_INVALIDA", DEMASIADAS_FOTOS);
		}
		if (archivos.isEmpty()) {
			return List.of();
		}
		return turnos.conTurno(() -> {
			List<ImagenUtil.Imagen> limpias = new ArrayList<>(archivos.size());
			for (int i = 0; i < archivos.size(); i++) {
				limpias.add(prepararFoto(archivos.get(i), i + 1));
			}
			return limpias;
		});
	}

	private static ImagenUtil.Imagen prepararFoto(MultipartFile archivo, int numero) {
		if (archivo.isEmpty()) {
			throw fotoInvalida(numero);
		}
		// Se mira el tamaño antes de leer el archivo.
		if (archivo.getSize() > FOTO_MAXIMO_BYTES) {
			throw fotoMuyPesada(numero);
		}
		byte[] bytes;
		try {
			bytes = archivo.getBytes();
		} catch (IOException e) {
			throw fotoInvalida(numero);
		}
		ImagenUtil.Imagen limpia = ImagenUtil.limpiar(bytes, FOTO_LADO_MAXIMO_PX);
		if (limpia == null) {
			throw fotoInvalida(numero);
		}
		if (limpia.bytes().length > FOTO_LIMPIA_MAXIMO_BYTES) {
			throw fotoMuyPesada(numero);
		}
		return limpia;
	}

	// Un solo mensaje para cualquier motivo técnico (formato, medidas, memoria, archivo roto): el vecino solo
	// necesita saber qué foto falló y qué hacer.
	private static PeticionInvalidaException fotoInvalida(int numero) {
		return new PeticionInvalidaException("FOTO_INVALIDA",
				"No pudimos usar la foto " + numero + ". Sacá otra o elegí una distinta.");
	}

	private static PeticionInvalidaException fotoMuyPesada(int numero) {
		return new PeticionInvalidaException("FOTO_INVALIDA",
				"La foto " + numero + " es muy pesada. Sacá otra o elegí una distinta.");
	}

	private static BigDecimal redondear(double coordenada) {
		return BigDecimal.valueOf(coordenada).setScale(DECIMALES_COORDENADAS, RoundingMode.HALF_UP);
	}
}
