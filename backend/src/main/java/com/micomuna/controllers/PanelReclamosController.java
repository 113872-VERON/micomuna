package com.micomuna.controllers;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.AgenteConCargaDto;
import com.micomuna.dtos.AsignarAgenteRequest;
import com.micomuna.dtos.CambiarTipoRequest;
import com.micomuna.dtos.CambioDeEstadoEnLoteRequest;
import com.micomuna.dtos.DescarteEnLoteRequest;
import com.micomuna.dtos.MapaReclamosDto;
import com.micomuna.dtos.MetricasDto;
import com.micomuna.dtos.PaginaReclamosPanelDto;
import com.micomuna.dtos.ReclamoPanelDetalleDto;
import com.micomuna.dtos.ResultadoEnLoteDto;
import com.micomuna.dtos.ResumenPanelDto;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.CambioDeEstadoService;
import com.micomuna.services.CambioDeTipoService;
import com.micomuna.services.CambiosEnLoteService;
import com.micomuna.services.MisReclamosService;
import com.micomuna.services.MetricasService;
import com.micomuna.services.PanelReclamosService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

// MC-18: el panel de reclamos del personal. Solo ADMINISTRATIVO y ADMIN_COMUNA (además del @PreAuthorize, SecurityConfig
// lo exige por URL para /api/panel/**: así otro rol recibe 403 antes de que se validen los parámetros o el cuerpo).
// La comuna sale del token (ComunaActual) y quien cambia un estado es el usuario del token, nunca uno del pedido.
@Tag(name = "Panel de reclamos", description = "Gestión de los reclamos de la comuna por el personal")
@RestController
@RequestMapping("/api/panel")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'ADMIN_COMUNA')")
public class PanelReclamosController {

	private static final String SIN_GUARDAR = "no-store";

	private final PanelReclamosService panel;
	private final CambiosEnLoteService lotes;
	private final CambioDeEstadoService cambios;
	private final CambioDeTipoService tipoDelReclamo;
	private final MetricasService metricasService;

	@Operation(operationId = "metricasDelPanel", summary = "Métricas simples de la comuna")
	@GetMapping("/metricas")
	public MetricasDto metricas(
			@Parameter(description = "AAAA-MM-DD, día argentino, inclusive") @RequestParam(required = false) String desde,
			@Parameter(description = "AAAA-MM-DD, día argentino, inclusive") @RequestParam(required = false) String hasta) {
		return metricasService.calcular(desde, hasta);
	}

	@Operation(operationId = "listarReclamosDelPanel", summary = "Reclamos de la comuna, con filtros")
	@GetMapping("/reclamos")
	public PaginaReclamosPanelDto listar(
			@RequestParam(required = false) EstadoReclamo estado,
			@RequestParam(required = false) String tipo,
			@RequestParam(required = false) Long agente,
			@RequestParam(required = false) String buscar,
			@RequestParam(defaultValue = "0") @Min(0) @Max(1000) int pagina,
			@RequestParam(defaultValue = "25") @Min(1) @Max(100) int tamanio) {
		return panel.listar(panel.filtros(estado, tipo, agente, buscar), pagina, tamanio);
	}

	@Operation(operationId = "mapaDeReclamosDelPanel", summary = "Puntos del mapa")
	@GetMapping("/reclamos/mapa")
	public MapaReclamosDto mapa(
			@RequestParam(required = false) EstadoReclamo estado,
			@RequestParam(required = false) String tipo,
			@RequestParam(required = false) Long agente,
			@RequestParam(required = false) String buscar) {
		return panel.mapa(panel.filtros(estado, tipo, agente, buscar));
	}

	@Operation(operationId = "resumenDelPanel", summary = "Cantidades por estado y reclamos sin asignar")
	@GetMapping("/reclamos/resumen")
	public ResumenPanelDto resumen(
			@RequestParam(required = false) String tipo,
			@RequestParam(required = false) Long agente,
			@RequestParam(required = false) String buscar) {
		return panel.resumen(panel.filtros(null, tipo, agente, buscar));
	}

	@Operation(operationId = "detalleDeReclamoDelPanel", summary = "Detalle de un reclamo para el personal")
	@GetMapping("/reclamos/{numero}")
	public ReclamoPanelDetalleDto detalle(@PathVariable Integer numero) {
		return panel.detalle(numero);
	}

	@Operation(operationId = "fotoDeReclamoDelPanel", summary = "Una foto de un reclamo de la comuna")
	@ApiResponse(responseCode = "200", description = "La imagen en binario", content = {
			@Content(mediaType = "image/jpeg", schema = @Schema(type = "string", format = "binary")),
			@Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")) })
	@GetMapping("/reclamos/{numero}/fotos/{idFoto}")
	public ResponseEntity<byte[]> foto(@PathVariable Integer numero, @PathVariable Long idFoto) {
		MisReclamosService.Imagen imagen = panel.foto(numero, idFoto);
		// Igual que las del vecino: tipo guardado por el servidor, nosniff, sin caché y sin Content-Disposition.
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(imagen.tipo()))
				.header("X-Content-Type-Options", "nosniff")
				.header(HttpHeaders.CACHE_CONTROL, SIN_GUARDAR)
				.body(imagen.bytes());
	}

	@Operation(operationId = "asignarAgente", summary = "Asignar o reasignar un reclamo a un agente")
	@PutMapping("/reclamos/{numero}/agente")
	public ReclamoPanelDetalleDto asignarAgente(@PathVariable Integer numero,
			@Valid @RequestBody AsignarAgenteRequest pedido,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
		// Quien asigna sale del token. La asignación se confirma en su propia transacción y el detalle se lee después.
		cambios.asignarAgente(numero, pedido.idAgente(), usuario.id());
		return panel.detalle(numero);
	}

	@Operation(operationId = "cambiarTipo", summary = "Cambiar el tipo de un reclamo (aceptar o corregir la sugerencia de la IA)")
	@PutMapping("/reclamos/{numero}/tipo")
	public ReclamoPanelDetalleDto cambiarTipo(@PathVariable Integer numero, @Valid @RequestBody CambiarTipoRequest pedido,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
		// Quien decide sale del token. El cambio se confirma en su propia transacción y el detalle se lee después.
		tipoDelReclamo.cambiarTipo(numero, pedido.tipo(), usuario.id());
		return panel.detalle(numero);
	}

	@Operation(operationId = "agentesDelPanel", summary = "Agentes de la comuna")
	@GetMapping("/agentes")
	public List<AgenteConCargaDto> agentes() {
		return panel.agentes();
	}

	@Operation(operationId = "cambiarEstadoDeReclamos", summary = "Cambiar el estado de uno o varios reclamos")
	@PostMapping("/reclamos/cambios-de-estado")
	public ResultadoEnLoteDto cambiarEstado(@Valid @RequestBody CambioDeEstadoEnLoteRequest pedido,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return lotes.cambiarEstado(pedido, usuario.id());
	}

	@Operation(operationId = "descartarReclamos", summary = "Descartar uno o varios reclamos")
	@PostMapping("/reclamos/descartes")
	public ResultadoEnLoteDto descartar(@Valid @RequestBody DescarteEnLoteRequest pedido,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return lotes.descartar(pedido, usuario.id());
	}
}
