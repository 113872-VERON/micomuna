package com.micomuna.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.ActualizarMisDatosRequest;
import com.micomuna.dtos.CambiarClaveRequest;
import com.micomuna.dtos.UsuarioDto;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.AuthService;
import com.micomuna.services.CuentaService;

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
	private final CuentaService cuentaService;

	@Operation(summary = "Ver mis datos", description = "Devuelve el nombre, el correo, el rol y la comuna de quien tiene la sesión iniciada. Vale para todos los roles, incluido el superadmin (sin comuna).")
	@ApiResponse(responseCode = "200", description = "Mis datos")
	@ApiResponse(responseCode = "401", description = "Sin sesión, o el token venció o no es válido")
	@GetMapping("/mis-datos")
	public UsuarioDto verMisDatos(@AuthenticationPrincipal UsuarioAutenticado usuario) {
		return cuentaService.verMisDatos(usuario);
	}

	@Operation(summary = "Cambiar mi nombre", description = "Cambia el nombre de quien tiene la sesión iniciada. Solo se puede cambiar el nombre: el correo, el rol y la comuna no se tocan desde acá. Devuelve los datos actualizados.")
	@ApiResponse(responseCode = "200", description = "Nombre cambiado")
	@ApiResponse(responseCode = "400", description = "Falta el nombre o supera los 100 caracteres (VALIDACION)")
	@ApiResponse(responseCode = "401", description = "Sin sesión, o el token venció o no es válido")
	@PutMapping("/mis-datos")
	public UsuarioDto cambiarMisDatos(@AuthenticationPrincipal UsuarioAutenticado usuario,
			@Valid @RequestBody ActualizarMisDatosRequest pedido) {
		return cuentaService.cambiarNombre(usuario, pedido);
	}

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
