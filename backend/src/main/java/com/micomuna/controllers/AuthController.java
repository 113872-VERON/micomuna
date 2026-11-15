package com.micomuna.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.AuthRespuesta;
import com.micomuna.dtos.LoginRequest;
import com.micomuna.dtos.RegistroRequest;
import com.micomuna.dtos.RecuperarClaveRequest;
import com.micomuna.dtos.RestablecerClaveRequest;
import com.micomuna.services.AuthService;
import com.micomuna.services.RecuperacionService;

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
	private final RecuperacionService recuperacionService;

	@Operation(summary = "Iniciar sesión en una comuna", description = "Devuelve el token y los datos del usuario. Cualquier falla de credenciales responde lo mismo: 401 «Credenciales incorrectas». El superadmin no entra por acá.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos")
	@ApiResponse(responseCode = "401", description = "Credenciales incorrectas")
	@ApiResponse(responseCode = "404", description = "La comuna no existe o está inactiva")
	@PostMapping("/login")
	public AuthRespuesta iniciarSesion(@PathVariable String slug, @Valid @RequestBody LoginRequest pedido) {
		return authService.login(slug, pedido);
	}

	@Operation(summary = "Pedir el correo para recuperar la contraseña", description = "Responde siempre 202, exista o no la cuenta, para no revelar qué correos están registrados. Si la cuenta existe y está activa, le llega un enlace de un solo uso que vence en 60 minutos. Quien todavía no eligió su contraseña recibe una invitación nueva.")
	@ApiResponse(responseCode = "202", description = "Pedido recibido")
	@ApiResponse(responseCode = "400", description = "Correo inválido")
	@ApiResponse(responseCode = "404", description = "La comuna no existe o está inactiva")
	@PostMapping("/recuperar-clave")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void recuperarClave(@PathVariable String slug, @Valid @RequestBody RecuperarClaveRequest pedido) {
		recuperacionService.solicitar(slug, pedido.correo());
	}

	@Operation(summary = "Elegir una contraseña nueva con el código del correo", description = "El código sirve una sola vez. Un código inexistente, vencido, usado o de otra comuna responde lo mismo: 400 CODIGO_INVALIDO.")
	@ApiResponse(responseCode = "204", description = "Contraseña cambiada")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o código inválido (CODIGO_INVALIDO)")
	@ApiResponse(responseCode = "404", description = "La comuna no existe o está inactiva")
	@PostMapping("/restablecer-clave")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void restablecerClave(@PathVariable String slug, @Valid @RequestBody RestablecerClaveRequest pedido) {
		recuperacionService.restablecer(slug, pedido);
	}

	@Operation(summary = "Aceptar una invitación eligiendo la contraseña", description = "Con el código del correo de invitación (vence en 72 horas y sirve una vez). Después la persona ingresa por el login normal.")
	@ApiResponse(responseCode = "204", description = "Contraseña elegida")
	@ApiResponse(responseCode = "400", description = "Datos inválidos o código inválido (CODIGO_INVALIDO)")
	@ApiResponse(responseCode = "404", description = "La comuna no existe o está inactiva")
	@PostMapping("/aceptar-invitacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void aceptarInvitacion(@PathVariable String slug, @Valid @RequestBody RestablecerClaveRequest pedido) {
		recuperacionService.aceptarInvitacion(slug, pedido);
	}

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
