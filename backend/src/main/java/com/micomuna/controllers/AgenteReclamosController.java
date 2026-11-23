package com.micomuna.controllers;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.PaginaReclamosAgenteDto;
import com.micomuna.dtos.ReclamoAgenteDetalleDto;
import com.micomuna.dtos.ResolverReclamoForm;
import com.micomuna.enums.GrupoReclamosAgente;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.AgenteReclamosService;
import com.micomuna.services.MisReclamosService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// MC-20: el agente de campo ve y resuelve SUS reclamos. Solo AGENTE (además del @PreAuthorize, SecurityConfig lo exige
// por URL para /api/agente/**: otro rol recibe 403 antes de que se validen los parámetros o el formulario).
// La comuna sale del token (ComunaActual) y el agente es el usuario del token, nunca uno del pedido.
@Tag(name = "Agente", description = "Reclamos asignados al agente de campo")
@RestController
@RequestMapping("/api/agente/reclamos")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('AGENTE')")
public class AgenteReclamosController {

	private static final String SIN_GUARDAR = "no-store";

	private final AgenteReclamosService agenteReclamos;

	@Operation(operationId = "misReclamosDelAgente", summary = "Mis reclamos asignados")
	@GetMapping
	public PaginaReclamosAgenteDto listar(
			@Parameter(description = "PARA_HACER (por defecto) o RESUELTOS") @RequestParam(defaultValue = "PARA_HACER") GrupoReclamosAgente grupo,
			@RequestParam(defaultValue = "0") @Min(0) @Max(1000) int pagina,
			@RequestParam(defaultValue = "50") @Min(1) @Max(100) int tamanio,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado agente) {
		return agenteReclamos.listar(grupo, pagina, tamanio, agente);
	}

	@Operation(operationId = "detalleDeReclamoDelAgente", summary = "Detalle de uno de mis reclamos")
	@GetMapping("/{numero}")
	public ReclamoAgenteDetalleDto detalle(@PathVariable Integer numero,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado agente) {
		return agenteReclamos.detalle(numero, agente);
	}

	@Operation(operationId = "fotoDeReclamoDelAgente", summary = "Una foto de uno de mis reclamos")
	@ApiResponse(responseCode = "200", description = "La imagen en binario", content = {
			@Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/{numero}/fotos/{idFoto}")
	public ResponseEntity<byte[]> foto(@PathVariable Integer numero, @PathVariable Long idFoto,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado agente) {
		MisReclamosService.Imagen imagen = agenteReclamos.foto(numero, idFoto, agente);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(imagen.tipo()))
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CACHE_CONTROL, SIN_GUARDAR)
				.body(imagen.bytes());
	}

	@Operation(operationId = "empezarReclamo", summary = "Empezar a trabajar")
	@PostMapping("/{numero}/empezar")
	public ReclamoAgenteDetalleDto empezar(@PathVariable Integer numero,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado agente) {
		return agenteReclamos.empezar(numero, agente);
	}

	@Operation(operationId = "resolverReclamo", summary = "Marcar como resuelto",
			requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = false, content = @Content(
					mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
					schema = @Schema(implementation = ResolverReclamoForm.class),
					encoding = @Encoding(name = "foto", contentType = "image/jpeg, image/png"))))
	@PostMapping("/{numero}/resolver")
	public ReclamoAgenteDetalleDto resolver(@PathVariable Integer numero,
			@Parameter(hidden = true) @Valid @ModelAttribute ResolverReclamoForm form,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado agente) {
		return agenteReclamos.resolver(numero, form, agente);
	}
}
