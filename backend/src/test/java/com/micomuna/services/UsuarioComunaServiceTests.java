package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.ActualizarUsuarioRequest;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.ConflictoException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.ComunaActual;
import com.micomuna.security.UsuarioAutenticado;

// Reglas de UsuarioComunaService que dependen de cambios simultáneos y no se pueden provocar con una sola petición:
// el bloqueo del usuario, la regla "la comuna nunca queda sin administradores con acceso" y quien actúa perdiendo
// el rol mientras esperaba el bloqueo de la comuna. La base se simula con Mockito.
@ExtendWith(MockitoExtension.class)
class UsuarioComunaServiceTests {

	private static final long COMUNA = 7L;

	@Mock
	private UsuarioRepository usuarios;
	@Mock
	private ComunaRepository comunas;
	@Mock
	private ComunaActual comunaActual;
	@Mock
	private CodigoService codigos;
	@Mock
	private InvitacionService invitaciones;
	@Mock
	private TransactionTemplate transaccion;

	@InjectMocks
	private UsuarioComunaService servicio;

	private final UsuarioAutenticado yo = new UsuarioAutenticado(1L, Rol.ADMIN_COMUNA, COMUNA);
	private Comuna comuna;
	private Usuario actor;
	private Usuario otraAdmin;

	@BeforeEach
	void preparar() {
		comuna = new Comuna();
		comuna.setId(COMUNA);
		actor = usuario(1L, Rol.ADMIN_COMUNA, "hash");
		otraAdmin = usuario(2L, Rol.ADMIN_COMUNA, "hash");
		when(comunaActual.id()).thenReturn(COMUNA);
		// Quien actúa, releído después de tomar el bloqueo de la comuna. Algunas pruebas lo cambian.
		lenient().when(usuarios.findByIdAndComunaId(1L, COMUNA)).thenReturn(Optional.of(actor));
	}

	private Usuario usuario(long id, Rol rol, String claveHash) {
		Usuario u = new Usuario();
		u.setId(id);
		u.setComuna(comuna);
		u.setCorreo("u" + id + "@mail.com");
		u.setNombre("Usuario " + id);
		u.setRol(rol);
		u.setClaveHash(claveHash);
		u.setActivo(true);
		return u;
	}

	private ActualizarUsuarioRequest desactivar() {
		return new ActualizarUsuarioRequest(null, false, null);
	}

	// ---- Bloqueo del usuario

	@Test
	void actualizarLeeAlUsuarioConBloqueoYConLaComunaDelToken() {
		Usuario agente = usuario(3L, Rol.AGENTE, "hash");
		when(usuarios.bloquearPorIdYComuna(3L, COMUNA)).thenReturn(Optional.of(agente));

		servicio.actualizar(3L, new ActualizarUsuarioRequest(Rol.VECINO, null, null), yo);

		verify(usuarios).bloquearPorIdYComuna(3L, COMUNA);
		// Ni sin bloqueo ni sin la comuna del token.
		verify(usuarios, never()).findByIdAndComunaId(eq(3L), anyLong());
		verify(usuarios, never()).findById(any());
		verify(usuarios).saveAndFlush(agente);
	}

	@Test
	void reenviarTambienLeeAlUsuarioConBloqueo() {
		Usuario pendiente = usuario(3L, Rol.AGENTE, null);
		when(transaccion.execute(any())).thenAnswer(i -> i.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
		when(usuarios.bloquearPorIdYComuna(3L, COMUNA)).thenReturn(Optional.of(pendiente));
		when(invitaciones.preparar(pendiente, comuna)).thenReturn(new InvitacionService.Invitacion(null, "codigo"));
		when(invitaciones.enviar(any())).thenReturn(true);

		servicio.reenviarInvitacion(3L);

		verify(usuarios).bloquearPorIdYComuna(3L, COMUNA);
		verify(usuarios, never()).findById(any());
	}

	@Test
	void unUsuarioQueNoEstaEnLaComunaDelTokenRespondeNoEncontrado() {
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> servicio.actualizar(2L, desactivar(), yo))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No encontramos ese usuario.");
		verify(usuarios, never()).findById(any());
	}

	// ---- Al menos un administrador activo con contraseña

	@Test
	void noSeDesactivaAlUltimoAdministradorConAcceso() {
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.of(otraAdmin));
		when(usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(COMUNA, Rol.ADMIN_COMUNA)).thenReturn(1L);

		assertThatThrownBy(() -> servicio.actualizar(2L, desactivar(), yo))
				.isInstanceOf(ConflictoException.class)
				.hasMessageContaining("al menos un administrador activo")
				.hasMessageEndingWith(".");

		assertThat(otraAdmin.isActivo()).isTrue();
		verify(usuarios, never()).saveAndFlush(any());
	}

	@Test
	void noSeLeQuitaElRolAlUltimoAdministradorConAcceso() {
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.of(otraAdmin));
		when(usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(COMUNA, Rol.ADMIN_COMUNA)).thenReturn(1L);

		assertThatThrownBy(() -> servicio.actualizar(2L, new ActualizarUsuarioRequest(Rol.AGENTE, null, null), yo))
				.isInstanceOf(ConflictoException.class);

		assertThat(otraAdmin.getRol()).isEqualTo(Rol.ADMIN_COMUNA);
		verify(usuarios, never()).saveAndFlush(any());
	}

	@Test
	void siQuedaOtroAdministradorConAccesoElCambioSeHace() {
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.of(otraAdmin));
		when(usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(COMUNA, Rol.ADMIN_COMUNA)).thenReturn(2L);

		var resultado = servicio.actualizar(2L, new ActualizarUsuarioRequest(Rol.AGENTE, null, null), yo);

		assertThat(resultado.rol()).isEqualTo(Rol.AGENTE);
		verify(usuarios).saveAndFlush(otraAdmin);
	}

	@Test
	void desactivarAUnAdministradorPendienteNoLeRestaAdministradoresConAcceso() {
		// El pendiente no está en la cuenta (todavía no tiene contraseña): queda el único que sí, el que actúa.
		Usuario adminPendiente = usuario(4L, Rol.ADMIN_COMUNA, null);
		when(usuarios.bloquearPorIdYComuna(4L, COMUNA)).thenReturn(Optional.of(adminPendiente));
		when(usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(COMUNA, Rol.ADMIN_COMUNA)).thenReturn(1L);

		servicio.actualizar(4L, desactivar(), yo);

		assertThat(adminPendiente.isActivo()).isFalse();
	}

	@Test
	void laCuentaSeHaceDespuesDeBloquearLaComuna() {
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.of(otraAdmin));
		when(usuarios.countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(COMUNA, Rol.ADMIN_COMUNA)).thenReturn(2L);

		servicio.actualizar(2L, desactivar(), yo);

		InOrder orden = inOrder(usuarios, comunas);
		orden.verify(usuarios).bloquearPorIdYComuna(2L, COMUNA);
		orden.verify(comunas).bloquear(COMUNA);
		orden.verify(usuarios).findByIdAndComunaId(1L, COMUNA);
		orden.verify(usuarios).countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(COMUNA, Rol.ADMIN_COMUNA);
	}

	@Test
	void cambiarAQuienNoEsAdministradorNoBloqueaLaComunaNiCuenta() {
		Usuario agente = usuario(3L, Rol.AGENTE, "hash");
		when(usuarios.bloquearPorIdYComuna(3L, COMUNA)).thenReturn(Optional.of(agente));

		servicio.actualizar(3L, desactivar(), yo);

		verify(usuarios, never()).countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(any(), any());
		verify(comunas, never()).bloquear(any());
	}

	// ---- Quien actúa perdió el acceso mientras esperaba el bloqueo de la comuna

	@Test
	void siQuienActuaPerdioElRolAntesDelBloqueoSeCortaCon403() {
		// Otro administrador le quitó el rol mientras esta petición esperaba.
		actor.setRol(Rol.AGENTE);
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.of(otraAdmin));

		assertThatThrownBy(() -> servicio.actualizar(2L, desactivar(), yo))
				.isInstanceOf(AccessDeniedException.class);

		assertThat(otraAdmin.isActivo()).isTrue();
		verify(usuarios, never()).countByComunaIdAndRolAndActivoTrueAndClaveHashIsNotNull(any(), any());
		verify(usuarios, never()).saveAndFlush(any());
	}

	@Test
	void siQuienActuaFueDesactivadoAntesDelBloqueoSeCortaCon403() {
		actor.setActivo(false);
		when(usuarios.bloquearPorIdYComuna(2L, COMUNA)).thenReturn(Optional.of(otraAdmin));

		assertThatThrownBy(() -> servicio.actualizar(2L, new ActualizarUsuarioRequest(Rol.VECINO, null, null), yo))
				.isInstanceOf(AccessDeniedException.class);

		assertThat(otraAdmin.getRol()).isEqualTo(Rol.ADMIN_COMUNA);
		verify(usuarios, never()).saveAndFlush(any());
	}
}
