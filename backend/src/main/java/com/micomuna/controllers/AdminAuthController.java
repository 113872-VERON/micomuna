package com.micomuna.controllers;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.AuthRespuesta;
import com.micomuna.dtos.LoginRequest;
import com.micomuna.services.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Administración", description = "Acceso del superadmin de la plataforma, que no pertenece a ninguna comuna")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AuthService authService;

	@Operation(summary = "Iniciar sesión como superadmin", description = "Solo el superadmin entra por acá. Cualquier falla de credenciales responde 401 «Credenciales incorrectas».")
	@ApiResponse(responseCode = "400", description = "Datos inválidos")
	@ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
	@PostMapping("/login")
	public AuthRespuesta iniciarSesion(@Valid @RequestBody LoginRequest pedido) {
		return authService.loginSuperadmin(pedido);
	}
}
