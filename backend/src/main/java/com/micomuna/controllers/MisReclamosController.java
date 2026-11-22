package com.micomuna.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.MiReclamoDetalleDto;
import com.micomuna.dtos.PaginaMisReclamosDto;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.MisReclamosService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// MC-17: el vecino sigue el estado de sus reclamos. Solo el vecino (además del @PreAuthorize, SecurityConfig lo exige
// por URL para que otro rol reciba 403 antes de que se validen los parámetros). La comuna sale del token (ComunaActual).
@Tag(name = "Reclamos", description = "Reclamos de los vecinos de una comuna")
@RestController
@RequestMapping("/api/reclamos/mios")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class MisReclamosController {

	private static final String CACHE_PRIVADO = "no-store";

	private final MisReclamosService misReclamos;

	@Operation(operationId = "listarMisReclamos", summary = "Mis reclamos")
	@ApiResponse(responseCode = "200", description = "OK (lista vacía si el vecino todavía no reportó nada)")
	@GetMapping
	@PreAuthorize("hasRole('VECINO')")
	public PaginaMisReclamosDto listar(
			@Parameter(description = "Número de página, empieza en 0") @RequestParam(defaultValue = "0") @Min(0) @Max(1000) int pagina,
			@Parameter(description = "Reclamos por página") @RequestParam(defaultValue = "20") @Min(1) @Max(50) int tamanio,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado vecino) {
		return misReclamos.listar(pagina, tamanio, vecino);
	}

	@Operation(operationId = "detalleDeMiReclamo", summary = "Detalle de uno de mis reclamos")
	@GetMapping("/{numero}")
	@PreAuthorize("hasRole('VECINO')")
	public MiReclamoDetalleDto detalle(@PathVariable Integer numero,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado vecino) {
		return misReclamos.detalle(numero, vecino);
	}

	@Operation(operationId = "fotoDeMiReclamo", summary = "Una foto de uno de mis reclamos")
	@ApiResponse(responseCode = "200", description = "La imagen en binario", content = {
			@Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/{numero}/fotos/{idFoto}")
	@PreAuthorize("hasRole('VECINO')")
	public ResponseEntity<byte[]> foto(@PathVariable Integer numero, @PathVariable Long idFoto,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado vecino) {
		MisReclamosService.Imagen imagen = misReclamos.foto(numero, idFoto, vecino);
		// Tipo guardado por el servidor, nunca uno que mande el cliente. Sin Content-Disposition: el navegador la muestra
		// como imagen y no la trata como un archivo para abrir.
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(imagen.tipo()))
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CACHE_CONTROL, CACHE_PRIVADO)
				.body(imagen.bytes());
	}
}
