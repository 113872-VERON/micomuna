package com.micomuna.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.AuthRespuesta;
import com.micomuna.dtos.RegistroRequest;
import com.micomuna.services.AuthService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Autenticación", description = "Registro e inicio de sesión de los usuarios de una comuna")
@RestController
@RequestMapping("/api/comunas/{slug}")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(summary = "Registrar un vecino", description = "Crea una cuenta de vecino en la comuna y devuelve el token para seguir con la sesión iniciada. El rol siempre es VECINO.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos")
	@ApiResponse(responseCode = "404", description = "La comuna no existe o está inactiva")
	@ApiResponse(responseCode = "409", description = "Ese correo ya está registrado en la comuna")
	@PostMapping("/registro")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthRespuesta registrar(@PathVariable String slug, @Valid @RequestBody RegistroRequest pedido) {
		return authService.registrarVecino(slug, pedido);
	}
}
