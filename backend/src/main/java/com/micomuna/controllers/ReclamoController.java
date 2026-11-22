package com.micomuna.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.CrearReclamoForm;
import com.micomuna.dtos.ReclamoCreadoDto;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.ReclamoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// La comuna del reclamo sale siempre del token (ComunaActual) y el vecino es el usuario autenticado.
// Además del @PreAuthorize, SecurityConfig restringe POST /api/reclamos al vecino por URL: así otro rol recibe 403
// antes de que se lea y se valide el formulario.
@Tag(name = "Reclamos", description = "Reclamos de los vecinos de una comuna")
@RestController
@RequestMapping("/api/reclamos")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class ReclamoController {

	private final ReclamoService reclamoService;

	@Operation(operationId = "crearReclamo", summary = "Reportar un problema", description = "Solo el vecino. Crea el reclamo en la comuna del token (nunca de lo que manda el cliente) con estado RECIBIDO y el número de seguimiento que le toca en esa comuna (correlativo, cada comuna empieza en 1). Formulario multipart. Latitud y longitud son obligatorias (el punto del mapa o del GPS); la referencia escrita y la dirección aproximada son opcionales. Si la comuna tiene un límite cargado, el punto tiene que estar adentro (un punto sobre el borde cuenta como adentro); si no tiene límite, no hay restricción. Hasta 3 fotos opcionales en el campo \"fotos\": cada una tiene que ser un JPG o PNG real (se decide por el contenido, no por lo que declara el cliente), de hasta 2 MB y hasta 2560 px de lado. El servidor vuelve a armar cada foto y no guarda sus datos ocultos (por ejemplo la ubicación GPS de la cámara). Cada vecino puede crear hasta 10 reclamos por hora.",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
					mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
					schema = @Schema(implementation = CrearReclamoForm.class),
					encoding = @Encoding(name = "fotos", contentType = "image/jpeg, image/png"))))
	@ApiResponse(responseCode = "201", description = "Reclamo creado")
	@ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDACION, con el motivo de cada campo en errores), tipo de problema inexistente (TIPO_INVALIDO), faltan latitud y longitud (UBICACION_REQUERIDA), el punto está fuera de la comuna (FUERA_DE_COMUNA), más de 3 fotos o una foto que no es un JPG o PNG real, pesa más de 2 MB, supera los 2560 px, ocuparía demasiada memoria al abrirla o pesaría más de 3 MB una vez limpia (FOTO_INVALIDA), o el pedido supera el tope de subida del servidor (ARCHIVO_MUY_GRANDE)")
	@ApiResponse(responseCode = "409", description = "Otra persona está reportando en este momento y no se pudo asignar el número (CONFLICTO). Probá de nuevo.")
	@ApiResponse(responseCode = "429", description = "El vecino ya creó 10 reclamos en la última hora (DEMASIADOS_RECLAMOS). Puede volver a intentar más tarde.")
	@ApiResponse(responseCode = "503", description = "El servidor está procesando muchas fotos a la vez (OCUPADO). Probá de nuevo en unos segundos.")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	@PreAuthorize("hasRole('VECINO')")
	public ReclamoCreadoDto crear(@Parameter(hidden = true) @Valid @ModelAttribute CrearReclamoForm form,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado vecino) {
		return reclamoService.crear(form, vecino);
	}
}
