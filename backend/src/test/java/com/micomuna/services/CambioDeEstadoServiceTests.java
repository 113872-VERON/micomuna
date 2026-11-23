package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.ReclamoEstado;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.exceptions.RecursoNoEncontradoException;
import com.micomuna.exceptions.TransicionInvalidaException;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

import jakarta.persistence.EntityManager;

// MC-65: el servicio único de cambio de estado. Contra la base de desarrollo, dentro de una transacción que se
// deshace al terminar cada prueba. Cubre las 25 combinaciones de estado (5 x 5): las permitidas cambian el reclamo y
// agregan su fila al historial; las demás se rechazan sin tocar nada.
@SpringBootTest
@Transactional
class CambioDeEstadoServiceTests {

	@Autowired
	private CambioDeEstadoService servicio;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private TipoReclamoRepository tipos;

	@Autowired
	private EntityManager em;

	@MockitoSpyBean
	private ReclamoRepository reclamos;

	private Comuna comunaA;
	private Comuna comunaB;
	private Usuario agente;
	private int proximoNumero;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t65-a");
		comunaB = comuna("t65-b");
		agente = usuario(comunaA, "agente-65@mail.com", Rol.AGENTE);
		proximoNumero = 1;
		entrarComo(agente);
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
	}

	// ---- Ayudas

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private void entrarComo(Usuario usuario) {
		UsuarioAutenticado principal = new UsuarioAutenticado(usuario.getId(), usuario.getRol(),
				usuario.getComuna().getId());
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
	}

	// Un reclamo ya en el estado dado (un DESCARTADO lleva su motivo, como exige la base).
	private Reclamo reclamo(Comuna comuna, EstadoReclamo estado) {
		Usuario vecino = usuario(comuna, "vecino-65-" + (++contadorUsuarios) + "@mail.com", Rol.VECINO);
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(proximoNumero++);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Un pozo");
		r.setLatitud(new java.math.BigDecimal("-31.420083"));
		r.setLongitud(new java.math.BigDecimal("-64.188776"));
		r.setEstado(estado);
		if (estado == EstadoReclamo.DESCARTADO) {
			r.setMotivoDescarte(MotivoDescarte.OTRO);
			r.setObservacionDescarte("Una observación");
		}
		reclamos.saveAndFlush(r);
		return r;
	}

	private List<ReclamoEstado> historialDe(Reclamo reclamo) {
		em.flush();
		em.clear();
		return em.createQuery("select e from ReclamoEstado e where e.reclamo.id = :id order by e.id", ReclamoEstado.class)
				.setParameter("id", reclamo.getId()).getResultList();
	}

	private Reclamo releido(Reclamo reclamo) {
		em.flush();
		em.clear();
		return em.find(Reclamo.class, reclamo.getId());
	}

	// El pedido que corresponde a cada estado de destino.
	private CambioDeEstado pedidoA(EstadoReclamo destino) {
		return switch (destino) {
			case DESCARTADO -> CambioDeEstado.descartar(MotivoDescarte.NO_SE_PUEDE_ATENDER, "No corresponde", null);
			case RESUELTO -> CambioDeEstado.resolver("Se rellenó el pozo");
			case ASIGNADO -> CambioDeEstado.asignar(agente.getId());
			default -> CambioDeEstado.a(destino);
		};
	}

	// Las 25 combinaciones (desde, hacia, permitida).
	static Stream<Arguments> combinaciones() {
		List<Arguments> todas = new ArrayList<>();
		for (EstadoReclamo desde : EstadoReclamo.values()) {
			for (EstadoReclamo hacia : EstadoReclamo.values()) {
				todas.add(Arguments.of(desde, hacia, desde.siguientesPermitidos().contains(hacia)));
			}
		}
		return todas.stream();
	}

	// ---- Transiciones

	@ParameterizedTest(name = "{0} -> {1}: permitida = {2}")
	@MethodSource("combinaciones")
	void cadaTransicionSeAceptaOSeRechazaSegunLasReglas(EstadoReclamo desde, EstadoReclamo hacia, boolean permitida) {
		Reclamo r = reclamo(comunaA, desde);
		int filasAntes = historialDe(r).size();

		if (permitida) {
			Instant antes = Instant.now().minusSeconds(1);
			servicio.cambiar(r.getNumero(), pedidoA(hacia), agente.getId());

			assertThat(releido(r).getEstado()).isEqualTo(hacia);
			List<ReclamoEstado> historial = historialDe(r);
			assertThat(historial).hasSize(filasAntes + 1);
			ReclamoEstado ultima = historial.get(historial.size() - 1);
			assertThat(ultima.getEstadoAnterior()).isEqualTo(desde);
			assertThat(ultima.getEstado()).isEqualTo(hacia);
			assertThat(ultima.getUsuario().getId()).isEqualTo(agente.getId());
			assertThat(ultima.getCambiadoEn()).isAfter(antes);
		} else {
			assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), pedidoA(hacia), agente.getId()))
					.isInstanceOf(TransicionInvalidaException.class);

			assertThat(releido(r).getEstado()).isEqualTo(desde);
			assertThat(historialDe(r)).hasSize(filasAntes);
		}
	}

	@Test
	void laTablaDeTransicionesEsLaAcordada() {
		assertThat(EstadoReclamo.RECIBIDO.siguientesPermitidos())
				.containsExactlyInAnyOrder(EstadoReclamo.ASIGNADO, EstadoReclamo.DESCARTADO);
		assertThat(EstadoReclamo.ASIGNADO.siguientesPermitidos())
				.containsExactlyInAnyOrder(EstadoReclamo.EN_REPARACION, EstadoReclamo.RECIBIDO, EstadoReclamo.DESCARTADO);
		assertThat(EstadoReclamo.EN_REPARACION.siguientesPermitidos())
				.containsExactlyInAnyOrder(EstadoReclamo.RESUELTO, EstadoReclamo.ASIGNADO);
		assertThat(EstadoReclamo.RESUELTO.siguientesPermitidos()).containsExactly(EstadoReclamo.EN_REPARACION);
		assertThat(EstadoReclamo.DESCARTADO.siguientesPermitidos()).containsExactly(EstadoReclamo.RECIBIDO);
	}

	@Test
	void pasarAlMismoEstadoSeRechaza() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), CambioDeEstado.a(EstadoReclamo.RECIBIDO), agente.getId()))
				.isInstanceOf(TransicionInvalidaException.class);
	}

	@Test
	void cambiarBloqueaLaFilaDelReclamo() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(agente.getId()), agente.getId());

		verify(reclamos, atLeastOnce()).bloquearPorNumero(comunaA.getId(), r.getNumero());
	}

	@Test
	void unaSecuenciaCompletaDejaTodoElHistorial() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		servicio.registrarAlta(r, r.getVecino());

		servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(agente.getId()), agente.getId());
		servicio.cambiar(r.getNumero(), CambioDeEstado.a(EstadoReclamo.EN_REPARACION), agente.getId());
		servicio.cambiar(r.getNumero(), CambioDeEstado.resolver("Listo"), agente.getId());
		servicio.cambiar(r.getNumero(), CambioDeEstado.a(EstadoReclamo.EN_REPARACION), agente.getId());

		assertThat(historialDe(r)).extracting(ReclamoEstado::getEstadoAnterior, ReclamoEstado::getEstado)
				.containsExactly(
						org.assertj.core.groups.Tuple.tuple(null, EstadoReclamo.RECIBIDO),
						org.assertj.core.groups.Tuple.tuple(EstadoReclamo.RECIBIDO, EstadoReclamo.ASIGNADO),
						org.assertj.core.groups.Tuple.tuple(EstadoReclamo.ASIGNADO, EstadoReclamo.EN_REPARACION),
						org.assertj.core.groups.Tuple.tuple(EstadoReclamo.EN_REPARACION, EstadoReclamo.RESUELTO),
						org.assertj.core.groups.Tuple.tuple(EstadoReclamo.RESUELTO, EstadoReclamo.EN_REPARACION));
	}

	@Test
	void laNotaInternaQuedaEnElHistorial() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		servicio.cambiar(r.getNumero(), new CambioDeEstado(EstadoReclamo.ASIGNADO, null, null, null, null, "  Para Pedro  ", agente.getId()),
				agente.getId());

		List<ReclamoEstado> historial = historialDe(r);
		assertThat(historial.get(historial.size() - 1).getObservacion()).isEqualTo("Para Pedro");
	}

	// ---- Alcance: comuna y existencia

	@Test
	void unReclamoDeOtraComunaOInexistenteEsNoEncontrado() {
		Reclamo ajeno = reclamo(comunaB, EstadoReclamo.RECIBIDO);

		// El agente es de la comuna A: el reclamo de la B no existe para él, aunque coincida el número.
		assertThatThrownBy(() -> servicio.cambiar(ajeno.getNumero(), CambioDeEstado.asignar(agente.getId()), agente.getId()))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.cambiar(99999, CambioDeEstado.asignar(agente.getId()), agente.getId()))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(releido(ajeno).getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
	}

	@Test
	void sinEstadoNuevoSeRechaza() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), CambioDeEstado.a(null), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.extracting(e -> ((PeticionInvalidaException) e).getCodigo()).isEqualTo("ESTADO_REQUERIDO");
	}

	// ---- Descarte

	private static String codigo(Throwable e) {
		return ((PeticionInvalidaException) e).getCodigo();
	}

	@Test
	void descartarExigeMotivo() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(null, "obs", null), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("MOTIVO_REQUERIDO"));
		assertThat(releido(r).getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
	}

	@Test
	void descartarGuardaMotivoYObservacion() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.NO_ES_DE_LA_COMUNA, "  Es de la ruta  ", null),
				agente.getId());

		Reclamo leido = releido(r);
		assertThat(leido.getEstado()).isEqualTo(EstadoReclamo.DESCARTADO);
		assertThat(leido.getMotivoDescarte()).isEqualTo(MotivoDescarte.NO_ES_DE_LA_COMUNA);
		assertThat(leido.getObservacionDescarte()).isEqualTo("Es de la ruta");
		assertThat(leido.getReclamoOriginal()).isNull();
	}

	@Test
	void unaObservacionDeDescarteMuyLargaSeRechaza() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		String larga = "x".repeat(CambioDeEstadoService.OBSERVACION_MAXIMA + 1);

		assertThatThrownBy(
				() -> servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.OTRO, larga, null), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("TEXTO_MUY_LARGO"));
	}

	@Test
	void duplicadoVinculaConElOriginalDeLaMismaComuna() {
		Reclamo original = reclamo(comunaA, EstadoReclamo.EN_REPARACION);
		Reclamo duplicado = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		servicio.cambiar(duplicado.getNumero(),
				CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()), agente.getId());

		Reclamo leido = releido(duplicado);
		assertThat(leido.getEstado()).isEqualTo(EstadoReclamo.DESCARTADO);
		assertThat(leido.getMotivoDescarte()).isEqualTo(MotivoDescarte.DUPLICADO);
		assertThat(leido.getReclamoOriginal().getNumero()).isEqualTo(original.getNumero());
		assertThat(releido(original).getEstado()).isEqualTo(EstadoReclamo.EN_REPARACION);
	}

	@Test
	void duplicadoExigeOriginal() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(
				() -> servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, null), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("ORIGINAL_REQUERIDO"));
	}

	@Test
	void elOriginalNoPuedeSerInexistenteNiDeOtraComunaNiElPropioNiUnDescartado() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		Reclamo deOtraComuna = reclamo(comunaB, EstadoReclamo.RECIBIDO);
		Reclamo descartado = reclamo(comunaA, EstadoReclamo.DESCARTADO);
		// El número del de la comuna B no existe en la A (la A tiene 1 y 3), así que ese número se busca solo en la A.
		int numeroInexistenteEnA = deOtraComuna.getNumero();
		assertThat(numeroInexistenteEnA).isEqualTo(2);

		for (Integer numeroOriginal : List.of(numeroInexistenteEnA, 99999, r.getNumero(), descartado.getNumero())) {
			assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
					CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, numeroOriginal), agente.getId()))
					.as("original " + numeroOriginal)
					.isInstanceOf(PeticionInvalidaException.class)
					.satisfies(e -> assertThat(codigo(e)).isEqualTo("ORIGINAL_INVALIDO"));
		}
		assertThat(releido(r).getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
	}

	@Test
	void unOriginalDeOtraComunaConElMismoNumeroNoSeVincula() {
		// Los números son por comuna: el 1 de la B no es el 1 de la A. Acá se arma un duplicado en la B y se verifica
		// que el original se busca en la comuna del reclamo y no en cualquier lado.
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		proximoNumero = 1;
		Reclamo mismoNumeroEnB = reclamo(comunaB, EstadoReclamo.RECIBIDO);
		assertThat(mismoNumeroEnB.getNumero()).isEqualTo(r.getNumero());
		Reclamo originalEnA = reclamo(comunaA, EstadoReclamo.ASIGNADO);

		servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, originalEnA.getNumero()),
				agente.getId());

		assertThat(releido(r).getReclamoOriginal().getComuna().getId()).isEqualTo(comunaA.getId());
	}

	@Test
	void soloUnDuplicadoLlevaOriginal() {
		Reclamo original = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
				CambioDeEstado.descartar(MotivoDescarte.OTRO, null, original.getNumero()), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("ORIGINAL_INVALIDO"));
	}

	@Test
	void reabrirUnDescartadoBorraMotivoObservacionYOriginal() {
		Reclamo original = reclamo(comunaA, EstadoReclamo.ASIGNADO);
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()),
				agente.getId());

		servicio.cambiar(r.getNumero(), CambioDeEstado.a(EstadoReclamo.RECIBIDO), agente.getId());

		Reclamo leido = releido(r);
		assertThat(leido.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
		assertThat(leido.getMotivoDescarte()).isNull();
		assertThat(leido.getObservacionDescarte()).isNull();
		assertThat(leido.getReclamoOriginal()).isNull();
	}

	@Test
	void losDatosDeDescarteSoloSeAceptanAlDescartar() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
				new CambioDeEstado(EstadoReclamo.ASIGNADO, MotivoDescarte.OTRO, null, null, null, null, agente.getId()), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class);
		assertThat(releido(r).getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
	}

	// ---- Resolución

	@Test
	void resolverGuardaLoRealizadoYSeConservaAlReabrir() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.EN_REPARACION);

		servicio.cambiar(r.getNumero(), CambioDeEstado.resolver("  Se arregló la cañería  "), agente.getId());
		assertThat(releido(r).getTrabajoRealizado()).isEqualTo("Se arregló la cañería");

		servicio.cambiar(r.getNumero(), CambioDeEstado.a(EstadoReclamo.EN_REPARACION), agente.getId());
		Reclamo reabierto = releido(r);
		assertThat(reabierto.getEstado()).isEqualTo(EstadoReclamo.EN_REPARACION);
		assertThat(reabierto.getTrabajoRealizado()).isEqualTo("Se arregló la cañería");
	}

	@Test
	void resolverConTextoEnBlancoGuardaNullYUnoMuyLargoSeRechaza() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.EN_REPARACION);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
				CambioDeEstado.resolver("x".repeat(CambioDeEstadoService.TRABAJO_MAXIMO + 1)), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("TEXTO_MUY_LARGO"));

		servicio.cambiar(r.getNumero(), CambioDeEstado.resolver("   "), agente.getId());
		Reclamo leido = releido(r);
		assertThat(leido.getEstado()).isEqualTo(EstadoReclamo.RESUELTO);
		assertThat(leido.getTrabajoRealizado()).isNull();
	}

	// ---- Alta

	@Test
	void registrarAltaDejaLaPrimeraFilaSinEstadoAnterior() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		servicio.registrarAlta(r, r.getVecino());

		List<ReclamoEstado> historial = historialDe(r);
		assertThat(historial).hasSize(1);
		assertThat(historial.get(0).getEstadoAnterior()).isNull();
		assertThat(historial.get(0).getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
		assertThat(historial.get(0).getUsuario().getId()).isEqualTo(r.getVecino().getId());
	}

	@Test
	void unUsuarioInexistenteODeOtraComunaNoPuedeCambiarElEstadoYNoSeGuardaNada() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		Usuario deOtraComuna = usuario(comunaB, "agente-65-b@mail.com", Rol.AGENTE);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(agente.getId()), deOtraComuna.getId()))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(agente.getId()), 999999999L))
				.isInstanceOf(RecursoNoEncontradoException.class);
		assertThat(historialDe(r)).isEmpty();
	}

	// ---- Asignación (MC-18)

	@Test
	void asignarGuardaElAgenteYVolverARecibidoLoQuita() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(agente.getId()), agente.getId());
		assertThat(releido(r).getAgente().getId()).isEqualTo(agente.getId());

		servicio.cambiar(r.getNumero(), CambioDeEstado.a(EstadoReclamo.RECIBIDO), agente.getId());
		assertThat(releido(r).getAgente()).isNull();
	}

	@Test
	void reasignarDesdeEnReparacionCambiaDeAgente() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.EN_REPARACION);
		Usuario otro = usuario(comunaA, "otro-agente-65@mail.com", Rol.AGENTE);

		servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(otro.getId()), agente.getId());

		assertThat(releido(r).getAgente().getId()).isEqualTo(otro.getId());
	}

	@Test
	void asignarExigeUnAgenteActivoDeLaComunaYSinEseDatoNoSeToca() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		Usuario inactivo = usuario(comunaA, "inactivo-65@mail.com", Rol.AGENTE);
		inactivo.setActivo(false);
		usuarios.saveAndFlush(inactivo);
		Usuario deOtraComuna = usuario(comunaB, "agente-b-65@mail.com", Rol.AGENTE);
		Usuario vecino = usuario(comunaA, "vecino-no-agente-65@mail.com", Rol.VECINO);
		Usuario admin = usuario(comunaA, "admin-65@mail.com", Rol.ADMIN_COMUNA);

		for (Long idAgente : java.util.Arrays.asList(null, inactivo.getId(), deOtraComuna.getId(), vecino.getId(),
				admin.getId(), 999999999L)) {
			assertThatThrownBy(() -> servicio.cambiar(r.getNumero(), CambioDeEstado.asignar(idAgente), agente.getId()))
					.as("agente " + idAgente)
					.isInstanceOf(PeticionInvalidaException.class)
					.satisfies(e -> assertThat(codigo(e)).isEqualTo("AGENTE_INVALIDO"));
		}
		Reclamo leido = releido(r);
		assertThat(leido.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
		assertThat(leido.getAgente()).isNull();
		assertThat(historialDe(r)).isEmpty();
	}

	@Test
	void unAgenteConUnEstadoQueNoEsAsignadoSeRechaza() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.ASIGNADO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
				new CambioDeEstado(EstadoReclamo.EN_REPARACION, null, null, null, null, null, agente.getId()), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("AGENTE_INVALIDO"));
		assertThat(releido(r).getEstado()).isEqualTo(EstadoReclamo.ASIGNADO);
	}

	// ---- Observación del descarte (MC-18)

	@Test
	void laObservacionDelDescarteEsObligatoriaSalvoConDuplicado() {
		Reclamo original = reclamo(comunaA, EstadoReclamo.RECIBIDO);
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		for (MotivoDescarte motivo : List.of(MotivoDescarte.OTRO, MotivoDescarte.NO_ES_DE_LA_COMUNA,
				MotivoDescarte.NO_SE_PUEDE_ATENDER)) {
			for (String observacion : new String[] { null, "", "   " }) {
				assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
						CambioDeEstado.descartar(motivo, observacion, null), agente.getId()))
						.isInstanceOf(PeticionInvalidaException.class)
						.satisfies(e -> assertThat(codigo(e)).isEqualTo("OBSERVACION_REQUERIDA"));
			}
		}
		assertThat(releido(r).getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);

		servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()),
				agente.getId());
		assertThat(releido(r).getObservacionDescarte()).isNull();
	}

	@Test
	void laObservacionDelDescarteTieneTopeDe300() {
		Reclamo r = reclamo(comunaA, EstadoReclamo.RECIBIDO);

		assertThatThrownBy(() -> servicio.cambiar(r.getNumero(),
				CambioDeEstado.descartar(MotivoDescarte.OTRO, "x".repeat(301), null), agente.getId()))
				.isInstanceOf(PeticionInvalidaException.class)
				.satisfies(e -> assertThat(codigo(e)).isEqualTo("TEXTO_MUY_LARGO"));
		servicio.cambiar(r.getNumero(), CambioDeEstado.descartar(MotivoDescarte.OTRO, "x".repeat(300), null),
				agente.getId());
		assertThat(releido(r).getObservacionDescarte()).hasSize(300);
	}
}
