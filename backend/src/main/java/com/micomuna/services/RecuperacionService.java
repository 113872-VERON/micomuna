package com.micomuna.services;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.RestablecerClaveRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.TipoCodigo;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.services.CorreoAccesoService.Destinatario;

import lombok.RequiredArgsConstructor;

// Recuperar la clave olvidada y aceptar una invitación. Los dos pasos reciben un código de un solo uso.
@Service
@RequiredArgsConstructor
public class RecuperacionService {

	private final ComunaService comunaService;
	private final UsuarioRepository usuarios;
	private final CodigoService codigos;
	private final CorreoAccesoService correo;
	private final PasswordEncoder encoder;
	private final TransactionTemplate transaccion;

	private record Aviso(Destinatario destinatario, TipoCodigo tipo, String codigo) {
	}

	// Siempre termina igual (sin error) exista o no la cuenta, para no revelar qué correos están registrados.
	// Quien todavía no eligió su contraseña recibe una invitación nueva en lugar de un código de recuperación.
	// El correo sale después de guardar: si falla, el pedido igual se considera hecho.
	public void solicitar(String slug, String correoPedido) {
		Comuna comuna = comunaService.buscarActivaPorSlug(slug);

		Aviso aviso = transaccion.execute(estado -> usuarios.findByComunaIdAndCorreo(comuna.getId(), correoPedido)
				.filter(Usuario::isActivo)
				.map(usuario -> {
					TipoCodigo tipo = usuario.getClaveHash() == null ? TipoCodigo.INVITACION : TipoCodigo.RECUPERACION;
					return new Aviso(new Destinatario(usuario.getCorreo(), usuario.getNombre(), comuna.getNombre(),
							comuna.getSlug(), usuario.getRol()), tipo, codigos.emitir(usuario, tipo));
				})
				.orElse(null));

		if (aviso != null) {
			correo.enviar(aviso.destinatario(), aviso.tipo(), aviso.codigo());
		}
	}

	@Transactional
	public void restablecer(String slug, RestablecerClaveRequest pedido) {
		definirClave(slug, pedido, TipoCodigo.RECUPERACION);
	}

	@Transactional
	public void aceptarInvitacion(String slug, RestablecerClaveRequest pedido) {
		definirClave(slug, pedido, TipoCodigo.INVITACION);
	}

	// Los tokens ya emitidos siguen valiendo hasta que venzan (no hay revocación).
	private void definirClave(String slug, RestablecerClaveRequest pedido, TipoCodigo tipo) {
		Comuna comuna = comunaService.buscarActivaPorSlug(slug);
		Usuario usuario = codigos.canjear(comuna.getId(), pedido.codigo(), tipo);
		usuario.setClaveHash(encoder.encode(pedido.claveNueva()));
		usuarios.save(usuario);
	}
}
