package com.micomuna.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Errores con el mismo formato en toda la API, en español y sin detalles internos.
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(RecursoNoEncontradoException.class)
	ProblemDetail recursoNoEncontrado(RecursoNoEncontradoException e) {
		return problema(HttpStatus.NOT_FOUND, "No encontrado", "NO_ENCONTRADO", e.getMessage());
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ProblemDetail rutaInexistente() {
		return problema(HttpStatus.NOT_FOUND, "No encontrado", "NO_ENCONTRADO", "No encontramos lo que buscás");
	}

	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
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
