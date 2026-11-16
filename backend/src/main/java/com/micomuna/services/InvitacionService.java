package com.micomuna.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.exceptions.CorreoNoEnviadoException;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.enums.TipoCodigo;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.services.CorreoAccesoService.Destinatario;

import lombok.RequiredArgsConstructor;

// Invitación por correo: la persona queda sin contraseña (pendiente) y la elige desde un enlace de un solo uso.
@Service
@RequiredArgsConstructor
public class InvitacionService {

	private final ComunaRepository comunas;
	private final UsuarioRepository usuarios;
	private final CodigoService codigos;
	private final CorreoAccesoService correo;
	private final TransactionTemplate transaccion;

	// Datos del correo ya listos, para mandarlo cuando la base terminó de guardar.
	public record Invitacion(Destinatario destinatario, String codigo) {
	}

	// Se llama dentro de la transacción que crea al usuario. El correo se manda recién después, con enviar().
	public Invitacion preparar(Usuario usuario, Comuna comuna) {
		String codigo = codigos.emitir(usuario, TipoCodigo.INVITACION);
		return new Invitacion(new Destinatario(usuario.getCorreo(), usuario.getNombre(), comuna.getNombre(), comuna.getSlug()),
				codigo);
	}

	public boolean enviar(Invitacion invitacion) {
		return correo.enviar(invitacion.destinatario(), TipoCodigo.INVITACION, invitacion.codigo());
	}

	// El superadmin reenvía la invitación al administrador que todavía no eligió su contraseña.
	// La invitación anterior queda sin efecto.
	public void reenviarAlAdministrador(Long comunaId) {
		Invitacion invitacion = transaccion.execute(estado -> {
			Comuna comuna = comunas.findById(comunaId)
					.orElseThrow(() -> new RecursoNoEncontradoException("No encontramos esa comuna"));
			Usuario admin = usuarios.findByComunaIdAndRol(comunaId, Rol.ADMIN_COMUNA).stream()
					.filter(u -> u.getClaveHash() == null && u.isActivo())
					.findFirst()
					.orElseThrow(() -> new ConflictoException(
							"El administrador de esta comuna ya eligió su contraseña. Que use «Olvidé mi contraseña» si no la recuerda"));
			return preparar(admin, comuna);
		});
		if (!enviar(invitacion)) {
			throw new CorreoNoEnviadoException("No pudimos enviar el correo. Probá de nuevo en unos minutos");
		}
	}
}
