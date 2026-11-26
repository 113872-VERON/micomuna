package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.micomuna.entities.SugerenciaIa;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.ProveedorIa;
import com.micomuna.enums.Rol;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.SugerenciaIaRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.CambioDeEstadoService;

import jakarta.persistence.EntityManager;

// MC-27: prioridad sugerida. La prioridad efectiva de un reclamo (la que fijó el personal o, si no, la sugerida por la IA en la
// sugerencia vigente), el PUT que la fija, el orden por prioridad de la lista del panel y lo que ven el agente y el vecino.
// Contra la base de desarrollo, dentro de una transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrioridadSugeridaTests {

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
	private SugerenciaIaRepository sugerencias;
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
	private Usuario adminA;
	private Usuario administrativoA;
	private String tokenAdminA;
	private String tokenAdministrativoA;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t27-prio-a");
		comunaB = comuna("t27-prio-b");
		vecinoA = usuario(comunaA, Rol.VECINO, "Vecina Ana");
		agenteA = usuario(comunaA, Rol.AGENTE, "Álvaro Agente");
		adminA = usuario(comunaA, Rol.ADMIN_COMUNA, "Admin Comuna");
		administrativoA = usuario(comunaA, Rol.ADMINISTRATIVO, "Admin Tivo");
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

	private Usuario usuario(Comuna comuna, Rol rol, String nombre) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo("m27-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Reclamo de tipo BACHE; a más número, más nuevo (el número 1 es el más viejo).
	private Reclamo reclamo(Comuna comuna, int numero) {
		Usuario vecino = comuna == comunaA ? vecinoA : usuario(comuna, Rol.VECINO, "Vecino B");
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Reclamo " + numero);
		r.setDireccion("Calle Falsa " + numero);
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

	// Reclamo con la prioridad ya fijada por el personal.
	private Reclamo reclamoConPrioridad(int numero, PrioridadIa prioridad) {
		Reclamo r = reclamo(numero);
		r.setPrioridad(prioridad);
		r.setUsuarioPrioridad(adminA);
		r.setPrioridadFijadaEn(Instant.now());
		reclamos.saveAndFlush(r);
		return r;
	}

	private void sugerirHecha(Reclamo r, PrioridadIa prioridad) {
		sugerencias.save(SugerenciaIa.hecha(r.getComuna().getId(), r.getId(), ProveedorIa.CLAUDE, "modelo-secreto", "BACHE",
				prioridad, "Se ve un pozo en la calle.", 100L, 20L));
		em.flush();
		esperar();
	}

	private void sugerirConError(Reclamo r) {
		sugerencias.save(SugerenciaIa.conError(r.getComuna().getId(), r.getId(), ProveedorIa.CLAUDE, "m", "falló", null, null));
		em.flush();
		esperar();
	}

	private void sugerirOmitida(Reclamo r) {
		sugerencias.save(SugerenciaIa.omitida(r.getComuna().getId(), r.getId()));
		em.flush();
		esperar();
	}

	// La fecha de creación sale de la hora: se espera un poco para que la fila siguiente sea claramente más nueva.
	private static void esperar() {
		try {
			Thread.sleep(15);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private void cambiar(Reclamo r, CambioDeEstado cambio) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(adminA.getId(), Rol.ADMIN_COMUNA, comunaA.getId()), null, List.of()));
		try {
			cambios.cambiar(r.getNumero(), cambio, adminA.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private ResultActions fijar(int numero, String cuerpo, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(put(RUTA + "/" + numero + "/prioridad").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(cuerpo));
	}

	private ResultActions fijar(int numero, String prioridad) throws Exception {
		return fijar(numero, "{\"prioridad\":\"" + prioridad + "\"}", tokenAdminA);
	}

	private ResultActions detalle(int numero) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(RUTA + "/" + numero).header("Authorization", "Bearer " + tokenAdminA));
	}

	private ResultActions listar(String consulta) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(RUTA + consulta).header("Authorization", "Bearer " + tokenAdminA));
	}

	private List<String> observacionesDePrioridad(Reclamo r) {
		return jdbc.queryForList("select observacion from reclamo_estado where id_reclamo = ? "
				+ "and observacion like 'Cambió la prioridad%' order by cambiado_en, id", String.class, r.getId());
	}

	private int filasDeHistorial(Reclamo r) {
		return jdbc.queryForObject("select count(*) from reclamo_estado where id_reclamo = ?", Integer.class, r.getId());
	}

	private String prioridadEnBase(Reclamo r) {
		return jdbc.queryForObject("select prioridad from reclamo where id = ?", String.class, r.getId());
	}

	private String estadoEnBase(Reclamo r) {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId());
	}

	private Object actualizadoEnBase(Reclamo r) {
		return jdbc.queryForObject("select actualizado_en from reclamo where id = ?", Object.class, r.getId());
	}

	// ---- Prioridad efectiva en el detalle

	@Test
	void sinSugerenciaNiPrioridadFijadaLaEfectivaEsNinguna() throws Exception {
		reclamo(1);

		detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad.valor").value(nullValue()))
				.andExpect(jsonPath("$.prioridad.origen").value("NINGUNA"))
				.andExpect(jsonPath("$.prioridad.fijadaPor").value(nullValue()))
				.andExpect(jsonPath("$.prioridad.fijadaEn").value(nullValue()))
				.andExpect(jsonPath("$.sugerenciaIa.prioridadSugerida").value(nullValue()));
	}

	@Test
	void conSugerenciaHechaLaEfectivaEsLaDeLaIaYLaSugerenciaLaMuestra() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, PrioridadIa.MEDIA);

		detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad.valor").value("MEDIA"))
				.andExpect(jsonPath("$.prioridad.origen").value("IA"))
				.andExpect(jsonPath("$.prioridad.fijadaPor").value(nullValue()))
				.andExpect(jsonPath("$.sugerenciaIa.prioridadSugerida").value("MEDIA"));
		assertThat(prioridadEnBase(r)).as("la sugerencia no se copia al reclamo").isNull();
	}

	@Test
	void conErrorOUnaOmitidaLaEfectivaEsNingunaYNoSeMuestraPrioridadSugerida() throws Exception {
		Reclamo conError = reclamo(1);
		Reclamo omitida = reclamo(2);
		sugerirConError(conError);
		sugerirOmitida(omitida);

		for (int numero : new int[] { 1, 2 }) {
			detalle(numero).andExpect(status().isOk())
					.andExpect(jsonPath("$.prioridad.valor").value(nullValue()))
					.andExpect(jsonPath("$.prioridad.origen").value("NINGUNA"))
					.andExpect(jsonPath("$.sugerenciaIa.estado").value("NO_DISPONIBLE"))
					.andExpect(jsonPath("$.sugerenciaIa.prioridadSugerida").value(nullValue()));
		}
	}

	@Test
	void seTomaLaSugerenciaVigenteYNoUnaHechaMasVieja() throws Exception {
		Reclamo hechaYDespuesError = reclamo(1);
		sugerirHecha(hechaYDespuesError, PrioridadIa.ALTA);
		sugerirConError(hechaYDespuesError);
		Reclamo dosHechas = reclamo(2);
		sugerirHecha(dosHechas, PrioridadIa.ALTA);
		sugerirHecha(dosHechas, PrioridadIa.BAJA);

		detalle(1).andExpect(jsonPath("$.prioridad.origen").value("NINGUNA"))
				.andExpect(jsonPath("$.prioridad.valor").value(nullValue()));
		detalle(2).andExpect(jsonPath("$.prioridad.origen").value("IA"))
				.andExpect(jsonPath("$.prioridad.valor").value("BAJA"));
	}

	@Test
	void laPrioridadFijadaPorElPersonalGanaSobreLaDeLaIaYMuestraQuienYCuando() throws Exception {
		Reclamo r = reclamoConPrioridad(1, PrioridadIa.BAJA);
		sugerirHecha(r, PrioridadIa.ALTA);

		detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad.valor").value("BAJA"))
				.andExpect(jsonPath("$.prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.prioridad.fijadaPor").value("Admin Comuna"))
				.andExpect(jsonPath("$.prioridad.fijadaEn").isNotEmpty())
				.andExpect(jsonPath("$.sugerenciaIa.prioridadSugerida").value("ALTA"));
	}

	// ---- PUT: fijar la prioridad

	@Test
	void fijarUnaPrioridadDistintaDeLaEfectivaLaGuardaYDejaConstanciaEnElHistorialSinTocarElEstado() throws Exception {
		Reclamo r = reclamo(1);
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		sugerirHecha(r, PrioridadIa.MEDIA);
		int antes = filasDeHistorial(r);

		fijar(1, "ALTA").andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.prioridad.fijadaPor").value("Admin Comuna"))
				.andExpect(jsonPath("$.prioridad.fijadaEn").isNotEmpty())
				.andExpect(jsonPath("$.historial", hasSize(antes + 1)))
				.andExpect(jsonPath("$.historial[" + antes + "].estadoAnterior").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[" + antes + "].estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[" + antes + "].usuarioNombre").value("Admin Comuna"))
				.andExpect(jsonPath("$.historial[" + antes + "].observacion").value("Cambió la prioridad de Media a Alta"))
				.andExpect(jsonPath("$.historial[" + antes + "].agente").value(nullValue()));

		assertThat(prioridadEnBase(r)).isEqualTo("ALTA");
		assertThat(estadoEnBase(r)).isEqualTo("ASIGNADO");
		assertThat(jdbc.queryForObject("select id_usuario_prioridad from reclamo where id = ?", Long.class, r.getId()))
				.isEqualTo(adminA.getId());
		assertThat(jdbc.queryForObject("select prioridad_fijada_en from reclamo where id = ?", Object.class, r.getId()))
				.isNotNull();
	}

	@Test
	void sinSugerenciaElHistorialDiceDeSinPrioridadAAlta() throws Exception {
		Reclamo r = reclamo(1);

		fijar(1, "ALTA").andExpect(status().isOk());

		assertThat(observacionesDePrioridad(r)).containsExactly("Cambió la prioridad de Sin prioridad a Alta");
	}

	@Test
	void unaNuevaPrioridadDelPersonalReemplazaALaAnteriorYQuedaEnElHistorial() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, PrioridadIa.ALTA);

		fijar(1, "BAJA").andExpect(status().isOk()).andExpect(jsonPath("$.prioridad.valor").value("BAJA"));
		fijar(1, "ALTA").andExpect(status().isOk()).andExpect(jsonPath("$.prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.prioridad.origen").value("PERSONAL"));

		assertThat(observacionesDePrioridad(r)).containsExactly("Cambió la prioridad de Alta a Baja",
				"Cambió la prioridad de Baja a Alta");
		assertThat(prioridadEnBase(r)).isEqualTo("ALTA");
	}

	@Test
	void confirmarLaSugeridaGuardaQuienYCuandoSinFilaDeHistorial() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, PrioridadIa.ALTA);
		int antes = filasDeHistorial(r);

		fijar(1, "ALTA").andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.prioridad.fijadaPor").value("Admin Comuna"))
				.andExpect(jsonPath("$.prioridad.fijadaEn").isNotEmpty())
				.andExpect(jsonPath("$.historial", hasSize(antes)));

		assertThat(prioridadEnBase(r)).isEqualTo("ALTA");
		assertThat(filasDeHistorial(r)).isEqualTo(antes);

		// Y ya confirmada, mandar la misma de nuevo no tiene nada que cambiar.
		fijar(1, "ALTA").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MISMA_PRIORIDAD"));
	}

	@Test
	void unaSugerenciaNuevaDeLaIaNoPisaLaPrioridadQueFijoElPersonal() throws Exception {
		Reclamo r = reclamo(1);
		fijar(1, "MEDIA").andExpect(status().isOk());
		sugerirHecha(r, PrioridadIa.ALTA);

		detalle(1).andExpect(jsonPath("$.prioridad.valor").value("MEDIA"))
				.andExpect(jsonPath("$.prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.sugerenciaIa.prioridadSugerida").value("ALTA"));
	}

	@Test
	void fijarLaPrioridadNoMueveActualizadoEnNiElEstado() throws Exception {
		Reclamo r = reclamo(1);
		Object antes = actualizadoEnBase(r);

		fijar(1, "ALTA").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECIBIDO"));

		assertThat(actualizadoEnBase(r)).isEqualTo(antes);
	}

	@Test
	void decidePersonalAdministrativoYQuienDecideSaleDelToken() throws Exception {
		Reclamo r = reclamo(1);

		fijar(1, "{\"prioridad\":\"ALTA\",\"idUsuario\":" + vecinoA.getId() + "}", tokenAdministrativoA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad.fijadaPor").value("Admin Tivo"));

		assertThat(jdbc.queryForObject("select id_usuario_prioridad from reclamo where id = ?", Long.class, r.getId()))
				.isEqualTo(administrativoA.getId());
	}

	@Test
	void unReclamoResueltoODescartadoNoSeReprioriza() throws Exception {
		Reclamo resuelto = reclamo(1);
		cambiar(resuelto, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(resuelto, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));
		Reclamo descartado = reclamo(2);
		cambiar(descartado, CambioDeEstado.descartar(com.micomuna.enums.MotivoDescarte.NO_ES_DE_LA_COMUNA, "No es de la comuna", null));

		for (int numero : new int[] { 1, 2 }) {
			fijar(numero, "ALTA").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RECLAMO_CERRADO"));
		}
		assertThat(prioridadEnBase(resuelto)).isNull();
		assertThat(prioridadEnBase(descartado)).isNull();
	}

	@Test
	void laMismaPrioridadQueYaFijoElPersonalDa409SinCambiarNada() throws Exception {
		Reclamo r = reclamoConPrioridad(1, PrioridadIa.MEDIA);
		int antes = filasDeHistorial(r);

		fijar(1, "MEDIA").andExpect(status().isConflict())
				.andExpect(jsonPath("$.codigo").value("MISMA_PRIORIDAD"));

		assertThat(filasDeHistorial(r)).isEqualTo(antes);
	}

	@Test
	void unCuerpoSinPrioridadOConUnValorInvalidoDa400ValidacionYNoCambiaNada() throws Exception {
		Reclamo r = reclamo(1);
		for (String cuerpo : new String[] { "{}", "{\"prioridad\":null}", "{\"prioridad\":\"\"}", "{\"prioridad\":\"URGENTE\"}",
				"{\"prioridad\":\"alta\"}" }) {
			fijar(1, cuerpo, tokenAdminA).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
		assertThat(prioridadEnBase(r)).isNull();
		assertThat(observacionesDePrioridad(r)).isEmpty();
	}

	@Test
	void vecinoYAgenteRecibenElMismoAccesoDenegado() throws Exception {
		Reclamo r = reclamo(1);
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));

		for (Usuario u : new Usuario[] { vecinoA, agenteA }) {
			fijar(1, "{\"prioridad\":\"ALTA\"}", jwt.generar(u)).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		}
		assertThat(prioridadEnBase(r)).isNull();
	}

	@Test
	void sinSesionDa401() throws Exception {
		reclamo(1);
		em.flush();
		em.clear();
		mockMvc.perform(put(RUTA + "/1/prioridad").contentType("application/json").content("{\"prioridad\":\"ALTA\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unReclamoDeOtraComunaOInexistenteDa404Igual() throws Exception {
		Reclamo deB = reclamo(comunaB, 5);

		fijar(5, "ALTA").andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
		fijar(999, "ALTA").andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

		assertThat(prioridadEnBase(deB)).isNull();
	}

	// ---- Lista del panel: orden por prioridad

	// Orden por prioridad efectiva: ALTA, MEDIA, BAJA y sin prioridad; a igual, el más viejo primero.
	// 1 sin nada; 2 IA BAJA; 3 PERSONAL ALTA; 4 IA ALTA; 5 PERSONAL MEDIA; 6 PERSONAL BAJA (la IA decía ALTA); 7 IA ALTA;
	// 8 una HECHA ALTA vieja y después ERROR (la vigente manda: sin prioridad).
	private void armarMezcla() {
		reclamo(1);
		sugerirHecha(reclamo(2), PrioridadIa.BAJA);
		sugerirHecha(reclamoConPrioridad(3, PrioridadIa.ALTA), PrioridadIa.BAJA);
		sugerirHecha(reclamo(4), PrioridadIa.ALTA);
		reclamoConPrioridad(5, PrioridadIa.MEDIA);
		sugerirHecha(reclamoConPrioridad(6, PrioridadIa.BAJA), PrioridadIa.ALTA);
		sugerirHecha(reclamo(7), PrioridadIa.ALTA);
		Reclamo ocho = reclamo(8);
		sugerirHecha(ocho, PrioridadIa.ALTA);
		sugerirConError(ocho);
		// Otra comuna: no aparece aunque sea ALTA.
		sugerirHecha(reclamo(comunaB, 9), PrioridadIa.ALTA);
	}

	@Test
	void ordenarPorPrioridadMezclaPersonalIaYNingunaYAIgualPrioridadElMasViejoPrimero() throws Exception {
		armarMezcla();

		listar("?orden=prioridad").andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(8))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(3, 4, 7, 5, 2, 6, 1, 8)))
				.andExpect(jsonPath("$.reclamos[0].prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.reclamos[0].prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.reclamos[1].prioridad.origen").value("IA"))
				.andExpect(jsonPath("$.reclamos[1].prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.reclamos[4].prioridad.valor").value("BAJA"))
				.andExpect(jsonPath("$.reclamos[4].prioridad.origen").value("IA"))
				.andExpect(jsonPath("$.reclamos[5].prioridad.valor").value("BAJA"))
				.andExpect(jsonPath("$.reclamos[5].prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.reclamos[6].prioridad.valor").value(nullValue()))
				.andExpect(jsonPath("$.reclamos[6].prioridad.origen").value("NINGUNA"))
				.andExpect(jsonPath("$.reclamos[7].prioridad.origen").value("NINGUNA"));
	}

	@Test
	void sinOrdenOConOrdenFechaSigueSiendoDelMasNuevoAlMasViejoYMuestraLaPrioridad() throws Exception {
		armarMezcla();

		listar("").andExpect(status().isOk())
				.andExpect(jsonPath("$.reclamos[*].numero", contains(8, 7, 6, 5, 4, 3, 2, 1)))
				.andExpect(jsonPath("$.reclamos[2].prioridad.valor").value("BAJA"))
				.andExpect(jsonPath("$.reclamos[2].prioridad.origen").value("PERSONAL"));
		listar("?orden=fecha").andExpect(status().isOk())
				.andExpect(jsonPath("$.reclamos[*].numero", contains(8, 7, 6, 5, 4, 3, 2, 1)));
	}

	@Test
	void elOrdenPorPrioridadRespetaFiltrosYPaginacion() throws Exception {
		armarMezcla();

		// Paginación: la página 1 de a 3 son el cuarto, quinto y sexto del orden completo.
		listar("?orden=prioridad&tamanio=3&pagina=1").andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(8))
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(5, 2, 6)));
		listar("?orden=prioridad&tamanio=3&pagina=2").andExpect(status().isOk())
				.andExpect(jsonPath("$.reclamos[*].numero", contains(1, 8)));

		// Filtros: por agente y por estado.
		cambiar(reclamos.findByComunaIdAndNumero(comunaA.getId(), 4).orElseThrow(), CambioDeEstado.asignar(agenteA.getId()));
		cambiar(reclamos.findByComunaIdAndNumero(comunaA.getId(), 2).orElseThrow(), CambioDeEstado.asignar(agenteA.getId()));
		cambiar(reclamos.findByComunaIdAndNumero(comunaA.getId(), 5).orElseThrow(), CambioDeEstado.asignar(agenteA.getId()));
		listar("?orden=prioridad&agente=" + agenteA.getId()).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(4, 5, 2)));
		listar("?orden=prioridad&estado=RECIBIDO&buscar=Falsa").andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(3, 7, 6, 1, 8)));
		listar("?orden=prioridad&estado=ASIGNADO&tamanio=2").andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(4, 5)));
	}

	@Test
	void unOrdenQueNoEsFechaNiPrioridadDa400Validacion() throws Exception {
		reclamo(1);

		listar("?orden=tipo").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
		listar("?orden=urgente").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	// ---- Agente y vecino

	@Test
	void elAgenteVeLaPrioridadEfectivaSinQuienLaFijoEnSuListaYSuDetalle() throws Exception {
		Reclamo personal = reclamoConPrioridad(1, PrioridadIa.MEDIA);
		Reclamo ia = reclamo(2);
		sugerirHecha(ia, PrioridadIa.ALTA);
		Reclamo ninguna = reclamo(3);
		for (Reclamo r : new Reclamo[] { personal, ia, ninguna }) {
			cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		}
		String tokenAgente = jwt.generar(agenteA);
		em.flush();
		em.clear();

		String lista = mockMvc.perform(get("/api/agente/reclamos").header("Authorization", "Bearer " + tokenAgente))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reclamos[*].numero", contains(1, 2, 3)))
				.andExpect(jsonPath("$.reclamos[0].prioridad.valor").value("MEDIA"))
				.andExpect(jsonPath("$.reclamos[0].prioridad.origen").value("PERSONAL"))
				.andExpect(jsonPath("$.reclamos[1].prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.reclamos[1].prioridad.origen").value("IA"))
				.andExpect(jsonPath("$.reclamos[2].prioridad.valor").value(nullValue()))
				.andExpect(jsonPath("$.reclamos[2].prioridad.origen").value("NINGUNA"))
				.andReturn().getResponse().getContentAsString();
		assertThat(lista).doesNotContain("fijadaPor", "fijadaEn", "Admin Comuna", "prioridadSugerida");

		String detalle = mockMvc.perform(get("/api/agente/reclamos/1").header("Authorization", "Bearer " + tokenAgente))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.prioridad.valor").value("MEDIA"))
				.andExpect(jsonPath("$.prioridad.origen").value("PERSONAL"))
				.andReturn().getResponse().getContentAsString();
		assertThat(detalle).doesNotContain("fijadaPor", "fijadaEn", "Admin Comuna");
		mockMvc.perform(get("/api/agente/reclamos/2").header("Authorization", "Bearer " + tokenAgente))
				.andExpect(jsonPath("$.prioridad.valor").value("ALTA"))
				.andExpect(jsonPath("$.prioridad.origen").value("IA"));
	}

	@Test
	void elVecinoNoVeLaPrioridadNiEnLaListaNiEnElDetalle() throws Exception {
		Reclamo r = reclamoConPrioridad(1, PrioridadIa.ALTA);
		sugerirHecha(r, PrioridadIa.BAJA);
		fijar(1, "MEDIA").andExpect(status().isOk());
		em.flush();
		em.clear();
		String token = "Bearer " + jwt.generar(vecinoA);

		String lista = mockMvc.perform(get("/api/reclamos/mios").header("Authorization", token)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String detalle = mockMvc.perform(get("/api/reclamos/mios/1").header("Authorization", token)).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		for (String cuerpo : new String[] { lista, detalle }) {
			assertThat(cuerpo).doesNotContainIgnoringCase("prioridad").doesNotContain("fijadaPor");
			assertThat(cuerpo).doesNotContain("Cambió la prioridad");
		}
	}
}
