package com.micomuna.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.CambiarClaveRequest;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Mi cuenta", description = "Acciones de quien ya inició sesión, sea cual sea su rol")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class CuentaController {

	private final AuthService authService;

	@Operation(summary = "Cambiar mi contraseña", description = "Cambia la contraseña de quien tiene la sesión iniciada. Los tokens ya emitidos siguen valiendo hasta que venzan.")
	@ApiResponse(responseCode = "204", description = "Contraseña cambiada")
	@ApiResponse(responseCode = "400", description = "La contraseña actual no es correcta, la nueva no cumple las reglas o es igual a la actual")
	@ApiResponse(responseCode = "401", description = "Sin sesión")
	@PostMapping("/cambiar-clave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void cambiarClave(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@Valid @RequestBody CambiarClaveRequest pedido) {
		authService.cambiarClave(usuario, pedido);
	}
}
