package com.micomuna.services;

import java.text.Collator;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.ActualizarUsuarioRequest;
import com.micomuna.dtos.CrearUsuarioRequest;
import com.micomuna.dtos.UsuarioComunaDto;
import com.micomuna.dtos.UsuarioCreadoDto;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.enums.TipoCodigo;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.CorreoNoEnviadoException;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

// El administrador de una comuna gestiona a los usuarios de SU comuna (MC-14).
// La comuna sale siempre del token (ComunaActual) y todo usuario se busca por id Y comuna:
// uno de otra comuna responde 404, igual que uno inexistente. Acá nunca se busca un usuario solo por id.
@Service
@RequiredArgsConstructor
public class UsuarioComunaService {

	// Los vecinos se registran solos y el superadmin no pertenece a ninguna comuna.
	private static final Set<Rol> ROLES_DE_ALTA = EnumSet.of(Rol.ADMINISTRATIVO, Rol.AGENTE, Rol.ADMIN_COMUNA);

	private static final String CORREO_REPETIDO = "Ya hay un usuario con ese correo en esta comuna.";

	private final UsuarioRepository usuarios;
	private final ComunaRepository comunas;
	private final ComunaActual comunaActual;
	private final CodigoService codigos;
	private final InvitacionService invitaciones;
	private final TransactionTemplate transaccion;

	// Ordenados por nombre sin distinguir mayúsculas ni tildes ("Ávila" junto a "Avila"); a igual nombre, por id.
	@Transactional(readOnly = true)
	public List<UsuarioComunaDto> listar() {
		Collator collator = Collator.getInstance(Locale.forLanguageTag("es-AR"));
		collator.setStrength(Collator.PRIMARY);
		return usuarios.findByComunaId(comunaActual.id()).stream()
				.sorted(Comparator.comparing(Usuario::getNombre, collator).thenComparing(Usuario::getId))
				.map(UsuarioComunaDto::desde)
				.toList();
	}

	// El usuario queda sin contraseña (pendiente) y elige la suya con la invitación. El correo sale después de
	// guardar: si falla, el usuario igual queda creado y la respuesta lo avisa (invitacionEnviada = false).
	public UsuarioCreadoDto crear(CrearUsuarioRequest pedido) {
		if (!ROLES_DE_ALTA.contains(pedido.rol())) {
			throw new PeticionInvalidaException("ROL_NO_PERMITIDO",
					"Solo se puede dar de alta a personal administrativo, agentes o administradores. "
							+ "Los vecinos se registran solos.");
		}
		Creado creado = transaccion.execute(estado -> crearEnTransaccion(pedido));
		return new UsuarioCreadoDto(creado.usuario(), invitaciones.enviar(creado.invitacion()));
	}

	private record Creado(UsuarioComunaDto usuario, InvitacionService.Invitacion invitacion) {
	}

	private Creado crearEnTransaccion(CrearUsuarioRequest pedido) {
		// La comuna es la del token: ComunaActual ya la contrastó con la base al autenticar.
		Comuna comuna = comunas.findById(comunaActual.id())
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa comuna."));
		if (usuarios.existsByComunaIdAndCorreo(comuna.getId(), pedido.correo())) {
			throw new ConflictoException(CORREO_REPETIDO);
		}

		Usuario usuario = new Usuario();
		usuario.setComuna(comuna);
		usuario.setCorreo(pedido.correo());
		usuario.setNombre(pedido.nombre());
		usuario.setRol(pedido.rol());
		usuario.setClaveHash(null);
		usuario.setActivo(true);
		try {
			usuarios.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Otra alta con el mismo correo entró entre la consulta y el guardado.
			throw new ConflictoException(CORREO_REPETIDO);
		}
		return new Creado(UsuarioComunaDto.desde(usuario), invitaciones.preparar(usuario, comuna));
	}

	// Cambio parcial de rol, activo y correo. "Yo" es siempre el usuario autenticado, nunca un dato del pedido.
	// El usuario se lee con su fila bloqueada: si dos administradores lo cambian a la vez (uno lo desactiva y otro
	// le cambia el rol), el segundo espera y parte de lo que guardó el primero, en vez de pisarlo.
	@Transactional
	public UsuarioComunaDto actualizar(Long id, ActualizarUsuarioRequest pedido, UsuarioAutenticado yo) {
		if (pedido.estaVacio()) {
			throw new PeticionInvalidaException("PEDIDO_VACIO", "No mandaste ningún cambio.");
		}
		if (pedido.rol() == Rol.SUPERADMIN) {
			throw new PeticionInvalidaException("ROL_NO_PERMITIDO", "Ese rol no se puede asignar a un usuario de la comuna.");
		}

		Long comunaId = comunaActual.id();
		Usuario usuario = bloquear(id, comunaId);

		boolean cambiaRol = pedido.rol() != null && pedido.rol() != usuario.getRol();
		boolean desactiva = Boolean.FALSE.equals(pedido.activo()) && usuario.isActivo();
		boolean cambiaCorreo = pedido.correo() != null && !pedido.correo().equals(usuario.getCorreo());

		if (usuario.getId().equals(yo.id())) {
			if (cambiaRol) {
				throw new ConflictoException("No podés cambiarte el rol a vos mismo. Pedíselo a otro administrador.");
			}
			if (desactiva) {
				throw new ConflictoException("No podés desactivar tu propia cuenta. Pedíselo a otro administrador.");
			}
		}

		boolean dejaDeSerAdminActivo = usuario.getRol() == Rol.ADMIN_COMUNA && usuario.isActivo()
				&& ((cambiaRol && pedido.rol() != Rol.ADMIN_COMUNA) || desactiva);
		if (dejaDeSerAdminActivo) {
			verificarQueQuedaOtroAdministrador(usuario, comunaId, yo);
		}

		if (cambiaCorreo) {
			corregirCorreo(usuario, pedido.correo(), comunaId);
		}
		if (cambiaRol) {
			usuario.setRol(pedido.rol());
		}
		if (pedido.activo() != null && pedido.activo() != usuario.isActivo()) {
			usuario.setActivo(pedido.activo());
			if (!pedido.activo()) {
				// Desactivar no borra nada, pero ningún enlace que ya haya salido (invitación o recuperación) sigue sirviendo.
				codigos.invalidar(usuario, null);
			}
		}

		try {
			usuarios.saveAndFlush(usuario);
		} catch (DataIntegrityViolationException e) {
			// Otro cambio dejó ese correo en uso entre la consulta y el guardado.
			throw new ConflictoException(CORREO_REPETIDO);
		}
		return UsuarioComunaDto.desde(usuario);
	}

	// Por el camino normal no debería fallar: quien actúa es un administrador activo y no puede quitarse el rol ni
	// desactivarse. Cubre a dos administradores que se desactivan entre sí al mismo tiempo: la fila de la comuna
	// queda bloqueada, así esos cambios se hacen de a uno, y el segundo vuelve a leer a quien actúa (el primero
	// pudo haberlo desactivado) y cuenta lo que dejó el primero.
	// Solo cuentan los administradores activos que ya eligieron su contraseña: un pendiente todavía no puede entrar.
	private void verificarQueQuedaOtroAdministrador(Usuario usuario, Long comunaId, UsuarioAutenticado yo) {
		comunas.bloquear(comunaId);

		// Lectura común (sin bloqueo): al tener el bloqueo de la comuna, ya ve lo que guardó quien lo tenía antes.
		boolean sigoSiendoAdminActivo = usuarios.findByIdAndComunaId(yo.id(), comunaId)
				.filter(actor -> actor.isActivo() && actor.getRol() == Rol.ADMIN_COMUNA)
				.isPresent();
		if (!sigoSiendoAdminActivo) {
			throw new AccessDeniedException("Quien actúa dejó de ser administrador activo de la comuna");
		}

		long conAcceso = usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(comunaId, Rol.ADMIN_COMUNA);
		long quedarian = usuario.getClaveHash() != null ? conAcceso - 1 : conAcceso;
		if (quedarian < 1) {
			throw new ConflictoException(
					"La comuna tiene que quedar con al menos un administrador activo que ya haya elegido su contraseña.");
		}
	}

	// Solo mientras la persona no eligió su contraseña: después, el correo es su usuario para ingresar.
	// La invitación anterior queda sin efecto; la nueva se manda aparte, con reenviar.
	private void corregirCorreo(Usuario usuario, String correoNuevo, Long comunaId) {
		if (usuario.getClaveHash() != null) {
			throw new ConflictoException(
					"Solo se puede corregir el correo de alguien que todavía no aceptó la invitación.");
		}
		if (usuarios.existsByComunaIdAndCorreo(comunaId, correoNuevo)) {
			throw new ConflictoException(CORREO_REPETIDO);
		}
		usuario.setCorreo(correoNuevo);
		codigos.invalidar(usuario, TipoCodigo.INVITACION);
	}

	// Genera un enlace nuevo y deja sin efecto el anterior. El correo sale cuando la base ya guardó el código.
	// También lee al usuario con bloqueo: así no se cruza con un cambio de correo o una desactivación simultáneos.
	public void reenviarInvitacion(Long id) {
		InvitacionService.Invitacion invitacion = transaccion.execute(estado -> {
			Usuario usuario = bloquear(id, comunaActual.id());
			if (usuario.getClaveHash() != null) {
				throw new ConflictoException(
						"Este usuario ya eligió su contraseña. Si no la recuerda, que use «Olvidé mi contraseña».");
			}
			if (!usuario.isActivo()) {
				throw new ConflictoException("Este usuario está desactivado. Reactivalo antes de reenviarle la invitación.");
			}
			return invitaciones.preparar(usuario, usuario.getComuna());
		});
		if (!invitaciones.enviar(Objects.requireNonNull(invitacion))) {
			throw new CorreoNoEnviadoException("No pudimos enviar el correo. Probá de nuevo en unos minutos.");
		}
	}

	// Por id Y comuna, con la fila bloqueada hasta el final de la transacción (espera como mucho 3 segundos).
	private Usuario bloquear(Long id, Long comunaId) {
		return usuarios.bloquearPorIdYComuna(id, comunaId)
				.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ese usuario."));
	}
}
