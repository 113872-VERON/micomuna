package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.CambioDeEstadoService;

import jakarta.persistence.EntityManager;

// MC-19 (subtarea MC-81): asignar y reasignar un reclamo a un agente desde el panel. Contra la base de desarrollo,
// dentro de una transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AsignarAgenteTests {

	private static final String RUTA = "/api/panel/reclamos";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ReclamoRepository reclamos;

	@Autowired
	private TipoReclamoRepository tipos;

	@Autowired
	private CambioDeEstadoService cambios;

	@Autowired
	private JwtService jwt;

	@Autowired
	private EntityManager em;

	@Autowired
	private JdbcTemplate jdbc;

	private Comuna comunaA;
	private Comuna comunaB;
	private Usuario vecinoA;
	private Usuario agenteA;
	private Usuario otroAgenteA;
	private Usuario agenteB;
	private Usuario adminA;
	private Usuario administrativoA;
	private String tokenAdminA;
	private String tokenAdministrativoA;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t19-asignar-a");
		comunaB = comuna("t19-asignar-b");
		vecinoA = usuario(comunaA, Rol.VECINO, "Vecina Ana", true);
		agenteA = usuario(comunaA, Rol.AGENTE, "Álvaro Agente", true);
		otroAgenteA = usuario(comunaA, Rol.AGENTE, "Zoe Agente", true);
		agenteB = usuario(comunaB, Rol.AGENTE, "Agente de B", true);
		adminA = usuario(comunaA, Rol.ADMIN_COMUNA, "Admin Comuna", true);
		administrativoA = usuario(comunaA, Rol.ADMINISTRATIVO, "Admin Tivo", true);
		tokenAdminA = jwt.generar(adminA);
		tokenAdministrativoA = jwt.generar(administrativoA);
	}

	// ---- Ayudas

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, Rol rol, String nombre, boolean activo) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo("m19-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private Reclamo reclamo(Comuna comuna, int numero) {
		Usuario vecino = comuna == comunaA ? vecinoA : usuario(comuna, Rol.VECINO, "Vecino B", true);
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Reclamo " + numero);
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setCreadoEn(Instant.now().minus(10, ChronoUnit.DAYS).plus(numero, ChronoUnit.HOURS));
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	private Reclamo reclamo(int numero) {
		return reclamo(comunaA, numero);
	}

	// Cambia de estado como el admin de la comuna A.
	private void cambiar(Reclamo r, CambioDeEstado cambio) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(adminA.getId(), Rol.ADMIN_COMUNA, comunaA.getId()), null, List.of()));
		try {
			cambios.cambiar(r.getNumero(), cambio, adminA.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private ResultActions asignar(int numero, String cuerpo, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(put(RUTA + "/" + numero + "/agente").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(cuerpo));
	}

	private ResultActions asignar(int numero, Long idAgente) throws Exception {
		return asignar(numero, "{\"idAgente\":" + idAgente + "}", tokenAdminA);
	}

	private ResultActions pedir(String ruta) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenAdminA));
	}

	private static String sinRuta(String problema) {
		return problema.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private String estadoDe(Reclamo r) {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId());
	}

	private int filasDeHistorial(Reclamo r) {
		return jdbc.queryForObject("select count(*) from reclamo_estado where id_reclamo = ?", Integer.class, r.getId());
	}

	// ---- Asignar y reasignar

	@Test
	void asignarDesdeRecibidoDejaAsignadoConElAgenteYUnaFilaDeHistorial() throws Exception {
		Reclamo r = reclamo(1);

		asignar(1, agenteA.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.agente.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.agente.nombre").value("Álvaro Agente"))
				.andExpect(jsonPath("$.historial", hasSize(2)))
				.andExpect(jsonPath("$.historial[1].estadoAnterior").value("RECIBIDO"))
				.andExpect(jsonPath("$.historial[1].estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[1].usuarioNombre").value("Admin Comuna"))
				.andExpect(jsonPath("$.historial[1].agente.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[1].agente.nombre").value("Álvaro Agente"))
				.andExpect(jsonPath("$.historial[1].agenteAnterior").value(nullValue()))
				.andExpect(jsonPath("$.historial[0].agente").value(nullValue()));
		assertThat(filasDeHistorial(r)).isEqualTo(2);
	}

	@Test
	void quienAsignaSaleDelToken() throws Exception {
		reclamo(1);

		asignar(1, "{\"idAgente\":" + agenteA.getId() + ",\"idUsuario\":" + vecinoA.getId() + "}", tokenAdministrativoA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.historial[1].usuarioNombre").value("Admin Tivo"));
	}

	@Test
	void reasignarDesdeAsignadoCambiaDeAgenteYGuardaElAnterior() throws Exception {
		Reclamo r = reclamo(1);
		asignar(1, agenteA.getId()).andExpect(status().isOk());

		asignar(1, otroAgenteA.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.agente.id").value(otroAgenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial", hasSize(3)))
				.andExpect(jsonPath("$.historial[2].estadoAnterior").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[2].estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[2].agente.id").value(otroAgenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[2].agenteAnterior.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[2].agenteAnterior.nombre").value("Álvaro Agente"))
				.andExpect(jsonPath("$.historial[1].agenteAnterior").value(nullValue()));
		assertThat(filasDeHistorial(r)).isEqualTo(3);
	}

	@Test
	void reasignarDesdeEnReparacionVuelveAAsignadoYElAnteriorSaleDeLasFilasPrevias() throws Exception {
		Reclamo r = reclamo(1);
		asignar(1, agenteA.getId()).andExpect(status().isOk());
		// Esta fila (ASIGNADO -> EN_REPARACION) no guarda agente: el anterior se deriva de la fila que asignó.
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		asignar(1, otroAgenteA.getId())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.agente.id").value(otroAgenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[*].estado", contains("RECIBIDO", "ASIGNADO", "EN_REPARACION", "ASIGNADO")))
				.andExpect(jsonPath("$.historial[2].agente").value(nullValue()))
				.andExpect(jsonPath("$.historial[3].estadoAnterior").value("EN_REPARACION"))
				.andExpect(jsonPath("$.historial[3].agenteAnterior.id").value(agenteA.getId().intValue()));
	}

	@Test
	void despuesDeVolverARecibidoUnaNuevaAsignacionNoTieneAgenteAnterior() throws Exception {
		Reclamo r = reclamo(1);
		asignar(1, agenteA.getId()).andExpect(status().isOk());
		cambiar(r, CambioDeEstado.a(EstadoReclamo.RECIBIDO));

		asignar(1, otroAgenteA.getId()).andExpect(status().isOk())
				.andExpect(jsonPath("$.historial[3].agente.id").value(otroAgenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[3].agenteAnterior").value(nullValue()));
	}

	// ---- Rechazos

	@Test
	void unReclamoResueltoODescartadoDa409ReclamoCerrado() throws Exception {
		Reclamo resuelto = reclamo(1);
		cambiar(resuelto, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(resuelto, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));
		Reclamo descartado = reclamo(2);
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));

		for (int numero : new int[] { 1, 2 }) {
			asignar(numero, otroAgenteA.getId()).andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("RECLAMO_CERRADO"));
		}
		assertThat(estadoDe(resuelto)).isEqualTo("RESUELTO");
		assertThat(estadoDe(descartado)).isEqualTo("DESCARTADO");
	}

	@Test
	void elMismoAgenteDa409YaAsignadoEnAsignadoYEnEnReparacion() throws Exception {
		Reclamo r = reclamo(1);
		asignar(1, agenteA.getId()).andExpect(status().isOk());

		asignar(1, agenteA.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("YA_ASIGNADO"));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		asignar(1, agenteA.getId()).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("YA_ASIGNADO"));

		assertThat(estadoDe(r)).isEqualTo("EN_REPARACION");
		assertThat(filasDeHistorial(r)).isEqualTo(3);
	}

	@Test
	void unAgenteInvalidoDa400SinTocarNada() throws Exception {
		Reclamo r = reclamo(1);
		Usuario inactivo = usuario(comunaA, Rol.AGENTE, "Inactivo", false);
		Usuario sinClave = usuario(comunaA, Rol.AGENTE, "Sin clave", true);
		sinClave.setClaveHash(null);
		usuarios.saveAndFlush(sinClave);
		Usuario vecino = usuario(comunaA, Rol.VECINO, "No agente", true);

		for (Long idAgente : List.of(agenteB.getId(), inactivo.getId(), sinClave.getId(), vecino.getId(), adminA.getId(),
				999999999L)) {
			asignar(1, idAgente).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("AGENTE_INVALIDO"));
		}
		assertThat(estadoDe(r)).isEqualTo("RECIBIDO");
		assertThat(filasDeHistorial(r)).isEqualTo(1);
	}

	@Test
	void sinAgenteEnElPedidoDa400Validacion() throws Exception {
		reclamo(1);

		for (String cuerpo : List.of("{}", "{\"idAgente\":null}", "")) {
			asignar(1, cuerpo, tokenAdminA).andExpect(status().is4xxClientError());
		}
		asignar(1, "{}", tokenAdminA).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void unIdDeAgenteCeroONegativoDa400Validacion() throws Exception {
		reclamo(1);

		for (String id : List.of("0", "-5")) {
			asignar(1, "{\"idAgente\":" + id + "}", tokenAdminA).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
	}

	@Test
	void unReclamoDeOtraComunaOInexistenteDa404Identico() throws Exception {
		Reclamo deB = reclamo(comunaB, 7);

		String inexistente = asignar(8, agenteA.getId()).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();
		String otraComuna = asignar(7, agenteA.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString();

		assertThat(sinRuta(otraComuna)).isEqualTo(sinRuta(inexistente));
		assertThat(estadoDe(deB)).isEqualTo("RECIBIDO");
		assertThat(filasDeHistorial(deB)).isEqualTo(1);
	}

	@Test
	void vecinoYAgenteRecibenSiempre403() throws Exception {
		reclamo(1);
		for (String token : List.of(jwt.generar(vecinoA), jwt.generar(agenteA))) {
			asignar(1, "{\"idAgente\":" + agenteA.getId() + "}", token).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			// 403 antes de validar el cuerpo.
			asignar(1, "{}", token).andExpect(status().isForbidden());
		}
		mockMvc.perform(put(RUTA + "/1/agente").contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void sinAsignarNingunMetodoDeEscrituraDeOtroTipoAnda() throws Exception {
		reclamo(1);
		mockMvc.perform(post(RUTA + "/1/agente").header("Authorization", "Bearer " + tokenAdminA)
				.contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
	}

	// ---- Agentes con carga

	@Test
	void enCursoCuentaAsignadosYEnReparacionDeCadaAgenteSoloDeLaComuna() throws Exception {
		Reclamo r1 = reclamo(1);
		Reclamo r2 = reclamo(2);
		Reclamo r3 = reclamo(3);
		Reclamo r4 = reclamo(4);
		Reclamo r5 = reclamo(5);
		cambiar(r1, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r2, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r2, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		// Resuelto: ya no está en curso.
		cambiar(r3, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r3, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r3, CambioDeEstado.resolver("Listo"));
		// Descartado: tampoco.
		cambiar(r4, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r4, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));
		cambiar(r5, CambioDeEstado.asignar(otroAgenteA.getId()));
		// Un reclamo de otra comuna con un agente de esa comuna no cuenta para la A.
		Reclamo deB = reclamo(comunaB, 1);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(usuario(comunaB, Rol.ADMIN_COMUNA, "Admin B", true).getId(), Rol.ADMIN_COMUNA,
						comunaB.getId()), null, List.of()));
		try {
			cambios.cambiar(deB.getNumero(), CambioDeEstado.asignar(agenteB.getId()),
					((UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).id());
		} finally {
			SecurityContextHolder.clearContext();
		}

		pedir("/api/panel/agentes")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(2)))
				.andExpect(jsonPath("$[0].nombre").value("Álvaro Agente"))
				.andExpect(jsonPath("$[0].enCurso").value(2))
				.andExpect(jsonPath("$[1].nombre").value("Zoe Agente"))
				.andExpect(jsonPath("$[1].enCurso").value(1));
	}

	@Test
	void unAgenteSinReclamosTieneEnCursoCeroYReasignarMueveLaCarga() throws Exception {
		reclamo(1);
		asignar(1, agenteA.getId()).andExpect(status().isOk());
		pedir("/api/panel/agentes").andExpect(jsonPath("$[0].enCurso").value(1)).andExpect(jsonPath("$[1].enCurso").value(0));

		asignar(1, otroAgenteA.getId()).andExpect(status().isOk());
		pedir("/api/panel/agentes").andExpect(jsonPath("$[0].enCurso").value(0)).andExpect(jsonPath("$[1].enCurso").value(1));
	}

	// ---- Lotes y vecino

	@Test
	void elLoteGuardaElAgenteEnLaFilaYUnoYaAsignadoSigueOmitidoConSuNombre() throws Exception {
		Reclamo libre = reclamo(1);
		Reclamo yaAsignado = reclamo(2);
		cambiar(yaAsignado, CambioDeEstado.asignar(agenteA.getId()));
		em.flush();
		em.clear();

		mockMvc.perform(post(RUTA + "/cambios-de-estado").header("Authorization", "Bearer " + tokenAdminA)
				.contentType("application/json")
				.content("{\"numeros\":[1,2],\"estado\":\"ASIGNADO\",\"idAgente\":" + otroAgenteA.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aplicados", contains(1)))
				.andExpect(jsonPath("$.omitidos[0].numero").value(2))
				.andExpect(jsonPath("$.omitidos[0].motivo").value("El n.º 2 ya está asignado a Álvaro Agente."));

		pedir(RUTA + "/1").andExpect(jsonPath("$.historial[1].agente.id").value(otroAgenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[1].agenteAnterior").value(nullValue()));
		assertThat(jdbc.queryForObject("select id_agente from reclamo_estado where id_reclamo = ? and estado = 'ASIGNADO'",
				Long.class, libre.getId())).isEqualTo(otroAgenteA.getId());
	}

	@Test
	void elVecinoNoVeAgenteNiNombresDelPersonal() throws Exception {
		Reclamo r = reclamo(1);
		asignar(1, agenteA.getId()).andExpect(status().isOk());
		asignar(1, otroAgenteA.getId()).andExpect(status().isOk());
		em.flush();
		em.clear();

		String cuerpo = mockMvc.perform(get("/api/reclamos/mios/1").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial", hasSize(3)))
				.andExpect(jsonPath("$.agente").doesNotExist())
				.andExpect(jsonPath("$.historial[0].agente").doesNotExist())
				.andExpect(jsonPath("$.historial[1].agente").doesNotExist())
				.andExpect(jsonPath("$.historial[2].agenteAnterior").doesNotExist())
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("Álvaro").doesNotContain("Zoe").doesNotContain("Admin Comuna")
				.doesNotContain("agente").doesNotContain("usuarioNombre");
		// Y la lista del vecino tampoco.
		String lista = mockMvc.perform(get("/api/reclamos/mios").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(lista).doesNotContain("Álvaro").doesNotContain("Zoe").doesNotContain("agente");
		assertThat(r.getNumero()).isEqualTo(1);
	}
}
