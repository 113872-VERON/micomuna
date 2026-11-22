package com.micomuna.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.ConsultaDireccion;
import com.micomuna.dtos.DireccionDto;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.DireccionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Ademas del @PreAuthorize, SecurityConfig restringe /api/ubicacion/** por URL: asi el superadmin recibe 403
// antes de que se validen los parametros (si no, un parametro invalido respondería 400).
@Tag(name = "Reclamos", description = "Reclamos de los vecinos de una comuna")
@RestController
@RequestMapping("/api/ubicacion")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class UbicacionController {

	private final DireccionService direcciones;

	@Operation(operationId = "direccionDeUnPunto", summary = "Dirección aproximada de un punto", description = "Para cualquier usuario de una comuna (no el superadmin). El backend la pide a Nominatim (OpenStreetMap), respetando su política de uso: como mucho un pedido por segundo en total y 10 por minuto por usuario, identificándose con el nombre de la app y guardando las direcciones ya pedidas. El celular del vecino no se conecta con el servicio externo. Es solo informativa: si el servicio no responde o no encuentra nada, responde 204 y el reclamo sigue sin dirección. Servicio público para uso moderado: alcanza para desarrollo y demostración; en producción conviene un proveedor contratado o un servidor propio.")
	@ApiResponse(responseCode = "200", description = "Dirección encontrada")
	@ApiResponse(responseCode = "204", description = "No hay dirección para ese punto, o el servicio externo no respondió a tiempo")
	@ApiResponse(responseCode = "400", description = "Latitud o longitud faltantes o fuera de rango (VALIDACION)")
	@ApiResponse(responseCode = "429", description = "Se pidieron demasiadas direcciones seguidas (DEMASIADOS_PEDIDOS: tope global de un pedido por segundo o de 10 por minuto por usuario; las direcciones ya guardadas no cuentan). El frontend lo trata como \"sin dirección\".")
	@GetMapping("/direccion")
	@PreAuthorize("hasAnyRole('VECINO', 'AGENTE', 'ADMINISTRATIVO', 'ADMIN_COMUNA')")
	public ResponseEntity<DireccionDto> direccion(@Parameter(hidden = true) @Valid @ModelAttribute ConsultaDireccion consulta,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado usuario) {
		return direcciones.direccionDe(usuario.id(), consulta.latitud(), consulta.longitud())
				.map(texto -> ResponseEntity.ok(new DireccionDto(texto)))
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
