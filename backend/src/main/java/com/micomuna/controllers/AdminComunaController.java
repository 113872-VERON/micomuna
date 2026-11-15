package com.micomuna.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.micomuna.dtos.ActualizarComunaRequest;
import com.micomuna.dtos.ComunaAdminDto;
import com.micomuna.dtos.ComunaCreadaDto;
import com.micomuna.dtos.CrearComunaRequest;
import com.micomuna.dtos.InvitacionRespuesta;
import com.micomuna.services.ComunaService;
import com.micomuna.services.InvitacionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Tag(name = "Administración", description = "Acceso del superadmin de la plataforma, que no pertenece a ninguna comuna")
@RestController
@RequestMapping("/api/admin/comunas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPERADMIN')")
@SecurityRequirement(name = "bearerAuth")
public class AdminComunaController {

	private final ComunaService comunaService;
	private final InvitacionService invitacionService;

	@Operation(summary = "Crear una comuna con su administrador", description = "Crea la comuna y a su administrador, sin contraseña: elige la suya con la invitación por correo. Si no se manda el enlace, se arma con el nombre y la provincia. Las comunas no se borran, solo se desactivan.")
	@ApiResponse(responseCode = "400", description = "Datos inválidos")
	@ApiResponse(responseCode = "403", description = "Solo el superadmin")
	@ApiResponse(responseCode = "409", description = "Ya existe una comuna con ese enlace")
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ComunaCreadaDto crear(@Valid @RequestBody CrearComunaRequest pedido) {
		return comunaService.crear(pedido);
	}

	@Operation(summary = "Reenviar la invitación al administrador de una comuna", description = "Para cuando el correo no salió o se perdió. Genera un enlace nuevo y deja sin efecto el anterior. La respuesta dice si el correo salió.")
	@ApiResponse(responseCode = "403", description = "Solo el superadmin")
	@ApiResponse(responseCode = "404", description = "La comuna no existe")
	@ApiResponse(responseCode = "409", description = "El administrador ya eligió su contraseña")
	@PostMapping("/{id}/reenviar-invitacion")
	public InvitacionRespuesta reenviarInvitacion(@PathVariable Long id) {
		return invitacionService.reenviarAlAdministrador(id);
	}

	@Operation(summary = "Listar todas las comunas", description = "Incluye las inactivas. No trae la imagen del logo.")
	@GetMapping
	public List<ComunaAdminDto> listar() {
		return comunaService.listar();
	}

	@Operation(summary = "Cambiar una comuna", description = "Cambio parcial de nombre, color, departamento y activa. El enlace no se puede cambiar. Desactivarla impide el ingreso de sus usuarios y su consulta pública.")
	@ApiResponse(responseCode = "404", description = "La comuna no existe")
	@PatchMapping("/{id}")
	public ComunaAdminDto actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarComunaRequest pedido) {
		return comunaService.actualizar(id, pedido);
	}
}
