package com.micomuna.exceptions;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;

// Errores con el mismo formato en toda la API, en español y sin detalles internos.
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail recursoNoEncontrado(RecursoNoEncontradoException e) {
		return problema(HttpStatus.NOT_FOUND, "No encontrado", "NO_ENCONTRADO", e.getMessage());
	}

	@ExceptionHandler(CredencialesInvalidasException.class)
	ProblemDetail credencialesInvalidas(CredencialesInvalidasException e) {
		return problema(HttpStatus.UNAUTHORIZED, "No autorizado", "CREDENCIALES_INVALIDAS", e.getMessage());
	}

	// Lo lanza @PreAuthorize cuando el rol no alcanza. Sin este manejador lo atraparía el de errores inesperados (500).
	@ExceptionHandler(AccessDeniedException.class)
	ProblemDetail accesoDenegado() {
		return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "ACCESO_DENEGADO",
				"No tenés permiso para hacer esto");
	}

	@ExceptionHandler(PeticionInvalidaException.class)
	ProblemDetail reglaDeNegocio(PeticionInvalidaException e) {
		return problema(HttpStatus.BAD_REQUEST, "Petición inválida", e.getCodigo(), e.getMessage());
	}

	@ExceptionHandler(CorreoNoEnviadoException.class)
	ProblemDetail correoNoEnviado(CorreoNoEnviadoException e) {
		return problema(HttpStatus.BAD_GATEWAY, "Correo no enviado", "CORREO_NO_ENVIADO", e.getMessage());
	}

	@ExceptionHandler(LimiteSuperadoException.class)
	ProblemDetail limiteSuperado(LimiteSuperadoException e) {
		return problema(HttpStatus.TOO_MANY_REQUESTS, "Demasiados pedidos", e.getCodigo(), e.getMessage());
	}

	@ExceptionHandler(ServicioOcupadoException.class)
	ProblemDetail servicioOcupado(ServicioOcupadoException e) {
		log.info("Servidor ocupado: {}", e.getMessage());
		return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servidor ocupado", "OCUPADO", e.getMessage());
	}

	@ExceptionHandler(ConflictoException.class)
	ProblemDetail conflicto(ConflictoException e) {
		return problema(HttpStatus.CONFLICT, "Conflicto", "CONFLICTO", e.getMessage());
	}

	// Venció la espera de un bloqueo de fila (ver constants.Bloqueo): otra persona está cambiando lo mismo.
	// Con Oracle (probado en BloqueosDeUsuarioTests), "FOR UPDATE WAIT 3" vencido responde ORA-00054, Hibernate lo
	// convierte en su LockTimeoutException y Spring la entrega como CannotAcquireLockException (subclase de
	// PessimisticLockingFailureException, que también cubre un deadlock). Las de JPA quedan por si alguna llega
	// sin traducir (por ejemplo, desde un EntityManager usado directamente).
	@ExceptionHandler({ PessimisticLockingFailureException.class, LockTimeoutException.class,
			PessimisticLockException.class })
	ProblemDetail bloqueoOcupado(Exception e) {
		log.info("Bloqueo de fila ocupado ({})", e.getClass().getSimpleName());
		return problema(HttpStatus.CONFLICT, "Conflicto", "CONFLICTO",
				"Otra persona está cambiando estos datos. Probá de nuevo en unos segundos.");
	}

	// Un mensaje por campo, para que el formulario marque cada error donde corresponde.
	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail datosInvalidos(MethodArgumentNotValidException e) {
		Map<String, String> errores = new LinkedHashMap<>();
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			// Un valor que no se pudo convertir (por ejemplo, letras en la latitud de un formulario multipart) trae
			// un mensaje técnico en inglés: se reemplaza por uno para la persona.
			errores.putIfAbsent(error.getField(),
					error.isBindingFailure() ? "El valor no es válido" : error.getDefaultMessage());
		}
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "VALIDACION",
				"Revisá los datos ingresados");
		problema.setProperty("errores", errores);
		return problema;
	}

	// Un cambio de estado que las reglas no permiten (CambioDeEstadoService). 409: el pedido está bien armado, pero
	// choca con el estado actual del reclamo.
	@ExceptionHandler(TransicionInvalidaException.class)
	ProblemDetail transicionInvalida() {
		return problema(HttpStatus.CONFLICT, "Conflicto", "TRANSICION_INVALIDA",
				"El reclamo no puede pasar a ese estado desde el que tiene ahora.");
	}

	// Parámetros sueltos fuera de rango (por ejemplo, ?tamanio=0 en "mis reclamos"): 400 con el motivo de cada uno,
	// en el mismo formato que los errores de un cuerpo inválido. Sin esto caería en el 500 de errores inesperados.
	@ExceptionHandler(HandlerMethodValidationException.class)
	ProblemDetail parametrosInvalidos(HandlerMethodValidationException e) {
		Map<String, String> errores = new LinkedHashMap<>();
		e.getParameterValidationResults().forEach(resultado -> {
			String nombre = resultado.getMethodParameter().getParameterName();
			resultado.getResolvableErrors().forEach(
					error -> errores.putIfAbsent(nombre == null ? "parametro" : nombre, error.getDefaultMessage()));
		});
		ProblemDetail problema = problema(HttpStatus.BAD_REQUEST, "Datos inválidos", "VALIDACION",
				"Revisá los datos ingresados");
		problema.setProperty("errores", errores);
		return problema;
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ProblemDetail metodoNoPermitido() {
		return problema(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", "METODO_NO_PERMITIDO",
				"Ese método no está permitido en esta ruta");
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ProblemDetail tipoNoSoportado() {
		return problema(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de contenido no soportado", "TIPO_NO_SOPORTADO",
				"El formato de los datos enviados no está soportado");
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail rutaInexistente() {
		return problema(HttpStatus.NOT_FOUND, "No encontrado", "NO_ENCONTRADO", "No encontramos lo que buscás");
	}

	// El servidor corta la subida antes de llegar al controlador (tope de spring.servlet.multipart).
	// Vale para cualquier subida de archivos, por eso el código y el mensaje no hablan de un archivo en particular.
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ProblemDetail archivoMuyGrande() {
		return problema(HttpStatus.BAD_REQUEST, "Petición inválida", "ARCHIVO_MUY_GRANDE",
				"El archivo es demasiado grande.");
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
			MissingServletRequestPartException.class, MissingServletRequestParameterException.class })
	ProblemDetail peticionInvalida() {
		return problema(HttpStatus.BAD_REQUEST, "Petición inválida", "PETICION_INVALIDA",
				"Los datos enviados no son válidos");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail errorInesperado(Exception e) {
		log.error("Error inesperado", e);
		return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error del servidor", "ERROR_INTERNO",
				"Ocurrió un error inesperado. Probá de nuevo en unos minutos");
	}

	private static ProblemDetail problema(HttpStatus estado, String titulo, String codigo, String detalle) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
		problema.setTitle(titulo);
		problema.setProperty("codigo", codigo);
		return problema;
	}
}
