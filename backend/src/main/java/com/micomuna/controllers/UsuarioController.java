package com.micomuna.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.ActualizarUsuarioRequest;
import com.micomuna.dtos.CrearUsuarioRequest;
import com.micomuna.dtos.UsuarioComunaDto;
import com.micomuna.dtos.UsuarioCreadoDto;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.UsuarioComunaService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// Solo el administrador de la comuna. La comuna con la que se trabaja sale siempre del token (ComunaActual).
@Tag(name = "Usuarios de la comuna", description = "El administrador de la comuna gestiona a los usuarios de su comuna")
@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_COMUNA')")
@SecurityRequirement(name = "bearerAuth")
public class UsuarioController {

	private final UsuarioComunaService usuarioService;

	@Operation(operationId = "listarUsuarios", summary = "Listar los usuarios de mi comuna", description = "Solo el administrador de la comuna. Devuelve todos los usuarios de la comuna del token (vecinos y personal), ordenados por nombre. La comuna sale siempre del token, nunca de un parámetro.")
	@GetMapping
	public List<UsuarioComunaDto> listar() {
		return usuarioService.listar();
	}

	@Operation(operationId = "crearUsuario", summary = "Dar de alta a una persona del personal", description = "Solo el administrador de la comuna. Crea al usuario en la comuna del token, sin contraseña (queda PENDIENTE), y le manda una invitación por correo para que elija la suya. Los vecinos no se dan de alta por acá: se registran solos. Si el correo no sale, el usuario igual queda creado y la respuesta trae invitacionEnviada = false.")
	@ApiResponse(responseCode = "201", description = "Usuario creado")
	@ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDACION) o rol VECINO o SUPERADMIN, que no se dan de alta por acá (ROL_NO_PERMITIDO)")
	@ApiResponse(responseCode = "409", description = "Ya hay un usuario con ese correo en la comuna (CONFLICTO)")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioCreadoDto crear(@Valid @RequestBody CrearUsuarioRequest pedido) {
		return usuarioService.crear(pedido);
	}

	@Operation(operationId = "actualizarUsuario", summary = "Cambiar el rol, el estado o el correo de un usuario", description = "Solo el administrador de la comuna. Cambio parcial: se manda solo lo que cambia. El rol puede ser cualquiera menos SUPERADMIN. Desactivar no borra: el usuario no puede iniciar sesión hasta que se lo reactive. El correo solo se puede corregir mientras el usuario está PENDIENTE; al corregirlo la invitación anterior queda sin efecto y hay que reenviarla. Un administrador no puede desactivarse ni quitarse el rol a sí mismo, ni dejar a la comuna sin administradores activos (409). Un usuario de otra comuna responde 404, igual que uno inexistente.")
	@ApiResponse(responseCode = "200", description = "Usuario actualizado")
	@ApiResponse(responseCode = "400", description = "Datos inválidos (VALIDACION), pedido sin ningún campo (PEDIDO_VACIO) o rol SUPERADMIN (ROL_NO_PERMITIDO)")
	@ApiResponse(responseCode = "404", description = "El usuario no existe o es de otra comuna (NO_ENCONTRADO)")
	@ApiResponse(responseCode = "409", description = "Cambio sobre uno mismo, la comuna quedaría sin administradores activos, correo de un usuario que ya no está pendiente o correo ya usado en la comuna u otra persona está cambiando estos datos (CONFLICTO, con el motivo en detail)")
	@PatchMapping("/{id}")
	public UsuarioComunaDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarUsuarioRequest pedido,
			@Parameter(hidden = true) @AuthenticationPrincipal UsuarioAutenticado yo) {
		return usuarioService.actualizar(id, pedido, yo);
	}

	@Operation(operationId = "reenviarInvitacionUsuario", summary = "Reenviar la invitación a un usuario pendiente", description = "Solo el administrador de la comuna. Genera un enlace nuevo y deja sin efecto el anterior. Si el correo no sale responde 502 CORREO_NO_ENVIADO.")
	@ApiResponse(responseCode = "204", description = "Invitación enviada")
	@ApiResponse(responseCode = "404", description = "El usuario no existe o es de otra comuna (NO_ENCONTRADO)")
	@ApiResponse(responseCode = "409", description = "El usuario ya eligió su contraseña, está inactivo u otra persona está cambiando estos datos (CONFLICTO)")
	@ApiResponse(responseCode = "502", description = "El correo no pudo enviarse (CORREO_NO_ENVIADO)")
	@PostMapping("/{id}/reenviar-invitacion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void reenviarInvitacion(@PathVariable Long id) {
		usuarioService.reenviarInvitacion(id);
	}
}
