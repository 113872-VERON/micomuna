package com.micomuna.services;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.ActualizarComunaRequest;
import com.micomuna.dtos.ComunaAdminDto;
import com.micomuna.dtos.ComunaCreadaDto;
import com.micomuna.dtos.CrearComunaRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ComunaService {

	private static final String ENLACE_REPETIDO = "Ya existe una comuna con ese enlace. "
			+ "Agregale el departamento al enlace para distinguirla (por ejemplo salsipuedes-colon-cordoba)";

	private final ComunaRepository comunas;
	private final UsuarioRepository usuarios;
	private final InvitacionService invitaciones;
	private final TransactionTemplate transaccion;

	public Comuna buscarActivaPorSlug(String slug) {
		if (!SlugUtil.esValido(slug)) {
			throw noEncontrada();
		}
		return comunas.findBySlug(slug)
				.filter(Comuna::isActiva)
				.orElseThrow(ComunaService::noEncontrada);
	}

	@Transactional(readOnly = true)
	public List<ComunaAdminDto> listar() {
		return comunas.listarParaAdmin();
	}

	// Crea la comuna, su administrador y el código de invitación en una sola transacción: si algo falla, no queda nada.
	// El administrador queda sin contraseña (pendiente). El correo sale después de guardar: si falla,
	// la comuna queda creada y la respuesta avisa (invitacionEnviada = false) para reenviarlo.
	public ComunaCreadaDto crear(CrearComunaRequest pedido) {
		Creada creada = transaccion.execute(estado -> crearEnTransaccion(pedido));
		return new ComunaCreadaDto(creada.comuna(), creada.adminCorreo(), invitaciones.enviar(creada.invitacion()));
	}

	private record Creada(ComunaAdminDto comuna, String adminCorreo, InvitacionService.Invitacion invitacion) {
	}

	private Creada crearEnTransaccion(CrearComunaRequest pedido) {
		String slug = pedido.slug() != null ? pedido.slug() : generarEnlace(pedido);
		if (comunas.existsBySlug(slug)) {
			throw new ConflictoException(ENLACE_REPETIDO);
		}

		Comuna comuna = new Comuna();
		comuna.setNombre(pedido.nombre());
		comuna.setProvincia(pedido.provincia());
		comuna.setSlug(slug);
		comuna.setColor(pedido.color() != null ? pedido.color() : ColorComuna.CELESTE);
		comuna.setDepartamento(pedido.departamento());
		comuna.setActiva(true);

		Usuario admin = new Usuario();
		admin.setComuna(comuna);
		admin.setCorreo(pedido.adminCorreo());
		admin.setNombre(pedido.adminNombre());
		admin.setRol(Rol.ADMIN_COMUNA);
		admin.setClaveHash(null);
		admin.setActivo(true);

		try {
			comunas.saveAndFlush(comuna);
			usuarios.saveAndFlush(admin);
		} catch (DataIntegrityViolationException e) {
			// Otro alta con el mismo enlace entró entre la consulta y el guardado.
			throw new ConflictoException(ENLACE_REPETIDO);
		}

		return new Creada(ComunaAdminDto.desde(comuna), admin.getCorreo(), invitaciones.preparar(admin, comuna));
	}

	// Solo cambia lo que viene en el pedido. El enlace no se toca: ya está repartido entre los vecinos.
	@Transactional
	public ComunaAdminDto actualizar(Long id, ActualizarComunaRequest pedido) {
		Comuna comuna = comunas.findById(id).orElseThrow(ComunaService::noEncontrada);

		if (pedido.nombre() != null) {
			comuna.setNombre(pedido.nombre());
		}
		if (pedido.activa() != null) {
			comuna.setActiva(pedido.activa());
		}
		if (pedido.color() != null) {
			comuna.setColor(pedido.color());
		}
		if (pedido.departamento() != null) {
			comuna.setDepartamento(pedido.departamento().isEmpty() ? null : pedido.departamento());
		}
		return ComunaAdminDto.desde(comunas.saveAndFlush(comuna));
	}

	private String generarEnlace(CrearComunaRequest pedido) {
		String slug;
		try {
			slug = SlugUtil.generar(pedido.nombre(), pedido.provincia());
		} catch (IllegalArgumentException e) {
			throw new PeticionInvalidaException("NOMBRE_INVALIDO", "El nombre tiene que tener letras o números");
		}
		if (!SlugUtil.esValido(slug)) {
			throw new PeticionInvalidaException("ENLACE_INVALIDO", "El enlace armado con ese nombre es demasiado largo");
		}
		return slug;
	}

	// Mismo error para "no existe" y "está inactiva", así no se revela cuáles existen.
	private static RecursoNoEncontradoException noEncontrada() {
		return new RecursoNoEncontradoException("No encontramos esa comuna");
	}
}
