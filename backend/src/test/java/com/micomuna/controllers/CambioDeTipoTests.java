package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Map;

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
import com.micomuna.enums.MotivoDescarte;
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

// MC-25: el personal acepta o corrige la sugerencia de tipo de la IA (PUT /api/panel/reclamos/{numero}/tipo) y la ve en el
// detalle. Contra la base de desarrollo, dentro de una transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CambioDeTipoTests {

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
		comunaA = comuna("t25-tipo-a");
		comunaB = comuna("t25-tipo-b");
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
		u.setCorreo("m25-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Reclamo de tipo BACHE.
	private Reclamo reclamo(Comuna comuna, int numero) {
		Usuario vecino = comuna == comunaA ? vecinoA : usuario(comuna, Rol.VECINO, "Vecino B");
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

	private void sugerirHecha(Reclamo r, String tipo) {
		sugerencias.save(SugerenciaIa.hecha(r.getComuna().getId(), r.getId(), ProveedorIa.CLAUDE, "modelo-secreto", tipo,
				PrioridadIa.ALTA, "Se ve un pozo en la calle.", 100L, 20L));
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

	private ResultActions cambiarTipo(int numero, String cuerpo, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(put(RUTA + "/" + numero + "/tipo").header("Authorization", "Bearer " + token)
				.contentType("application/json").content(cuerpo));
	}

	private ResultActions cambiarTipo(int numero, String tipo) throws Exception {
		return cambiarTipo(numero, "{\"tipo\":\"" + tipo + "\"}", tokenAdminA);
	}

	private ResultActions detalle(int numero) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(RUTA + "/" + numero).header("Authorization", "Bearer " + tokenAdminA));
	}

	private static String sinRuta(String problema) {
		return problema.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private String tipoEnBase(Reclamo r) {
		return jdbc.queryForObject("select t.codigo from reclamo r join tipo_reclamo t on t.id = r.id_tipo where r.id = ?",
				String.class, r.getId());
	}

	private String estadoEnBase(Reclamo r) {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId());
	}

	private int filasDeHistorial(Reclamo r) {
		return jdbc.queryForObject("select count(*) from reclamo_estado where id_reclamo = ?", Integer.class, r.getId());
	}

	// La decisión de la sugerencia más nueva: decision, tipo_decidido, id_usuario_decision.
	private Map<String, Object> decisionEnBase(Reclamo r) {
		return jdbc.queryForMap("""
				select decision, tipo_decidido, id_usuario_decision from reclamo_sugerencia_ia
				where id_reclamo = ? order by creada_en desc, id desc fetch first 1 rows only
				""", r.getId());
	}

	// ---- Detalle: sugerenciaIa

	@Test
	void sinAnalisisElDetalleDiceSinAnalizar() throws Exception {
		reclamo(1);

		detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.sugerenciaIa.estado").value("SIN_ANALIZAR"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoSugerido").value(nullValue()))
				.andExpect(jsonPath("$.sugerenciaIa.justificacion").value(nullValue()))
				.andExpect(jsonPath("$.sugerenciaIa.coincide").value(false))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value(nullValue()));
	}

	@Test
	void unaSugerenciaConErrorUOmitidaSeMuestraComoNoDisponibleSinDatosInternos() throws Exception {
		Reclamo conError = reclamo(1);
		Reclamo omitida = reclamo(2);
		sugerencias.save(SugerenciaIa.conError(comunaA.getId(), conError.getId(), ProveedorIa.CLAUDE, "modelo-secreto",
				"detalle interno del fallo", 5L, 6L));
		sugerencias.save(SugerenciaIa.omitida(comunaA.getId(), omitida.getId()));
		em.flush();

		for (int numero : new int[] { 1, 2 }) {
			String cuerpo = detalle(numero).andExpect(status().isOk())
					.andExpect(jsonPath("$.sugerenciaIa.estado").value("NO_DISPONIBLE"))
					.andExpect(jsonPath("$.sugerenciaIa.tipoSugerido").value(nullValue()))
					.andExpect(jsonPath("$.sugerenciaIa.tipoSugeridoNombre").value(nullValue()))
					.andExpect(jsonPath("$.sugerenciaIa.justificacion").value(nullValue()))
					.andExpect(jsonPath("$.sugerenciaIa.coincide").value(false))
					.andReturn().getResponse().getContentAsString();
			assertThat(cuerpo).doesNotContain("detalle interno", "modelo-secreto", "tokens", "prioridad", "CLAUDE");
		}
	}

	@Test
	void unaSugerenciaHechaMuestraTipoNombreJustificacionYSiCoincideConElTipoActual() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");

		String cuerpo = detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.sugerenciaIa.estado").value("HECHA"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoSugerido").value("LUZ"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoSugeridoNombre").value(tipos.findByCodigo("LUZ").orElseThrow().getNombre()))
				.andExpect(jsonPath("$.sugerenciaIa.justificacion").value("Se ve un pozo en la calle."))
				.andExpect(jsonPath("$.sugerenciaIa.coincide").value(false))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value(nullValue()))
				.andExpect(jsonPath("$.sugerenciaIa.decididaPor").value(nullValue()))
				.andExpect(jsonPath("$.sugerenciaIa.decididaEn").value(nullValue()))
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("modelo-secreto", "prioridad", "tokens");

		// Con una sugerencia igual al tipo del vecino, coincide.
		Reclamo igual = reclamo(2);
		sugerirHecha(igual, "BACHE");
		detalle(2).andExpect(jsonPath("$.sugerenciaIa.coincide").value(true));
	}

	@Test
	void laSugerenciaVigenteEsLaMasNueva() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");
		sugerencias.save(SugerenciaIa.conError(comunaA.getId(), r.getId(), ProveedorIa.CLAUDE, "m", "falló", null, null));
		em.flush();
		esperar();

		detalle(1).andExpect(jsonPath("$.sugerenciaIa.estado").value("NO_DISPONIBLE"));
	}

	// ---- Aceptar, corregir y reemplazar

	@Test
	void aceptarLaSugerenciaCambiaElTipoYRegistraLaDecisionConQuienYCuando() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");

		cambiarTipo(1, "LUZ").andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("LUZ"))
				.andExpect(jsonPath("$.estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.sugerenciaIa.estado").value("HECHA"))
				.andExpect(jsonPath("$.sugerenciaIa.coincide").value(true))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value("ACEPTADA"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoDecidido").value("LUZ"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoDecididoNombre").value(tipos.findByCodigo("LUZ").orElseThrow().getNombre()))
				.andExpect(jsonPath("$.sugerenciaIa.decididaPor").value("Admin Comuna"))
				.andExpect(jsonPath("$.sugerenciaIa.decididaEn").isNotEmpty());

		assertThat(tipoEnBase(r)).isEqualTo("LUZ");
		Map<String, Object> decision = decisionEnBase(r);
		assertThat(decision.get("DECISION")).isEqualTo("ACEPTADA");
		assertThat(((Number) decision.get("ID_USUARIO_DECISION")).longValue()).isEqualTo(adminA.getId());
	}

	@Test
	void corregirConOtroTipoQueNoEsElSugeridoQuedaComoCorregida() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");
		String otro = tipos.findAllByOrderByOrdenAsc().stream().map(t -> t.getCodigo())
				.filter(c -> !c.equals("LUZ") && !c.equals("BACHE")).findFirst().orElseThrow();

		cambiarTipo(1, otro).andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value(otro))
				.andExpect(jsonPath("$.sugerenciaIa.coincide").value(false))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value("CORREGIDA"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoDecidido").value(otro))
				.andExpect(jsonPath("$.sugerenciaIa.decididaPor").value("Admin Comuna"));

		assertThat(tipoEnBase(r)).isEqualTo(otro);
		assertThat(decisionEnBase(r).get("DECISION")).isEqualTo("CORREGIDA");
	}

	@Test
	void mantenerElTipoDelVecinoRegistraUnaCorreccionSinCambiarElTipoNiElHistorial() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");

		cambiarTipo(1, "BACHE").andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("BACHE"))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value("CORREGIDA"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoDecidido").value("BACHE"))
				.andExpect(jsonPath("$.historial", hasSize(1)));

		assertThat(tipoEnBase(r)).isEqualTo("BACHE");
		assertThat(filasDeHistorial(r)).isEqualTo(1);
		assertThat(decisionEnBase(r).get("DECISION")).isEqualTo("CORREGIDA");
	}

	@Test
	void unaNuevaDecisionReemplazaALaAnterior() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");
		cambiarTipo(1, "LUZ").andExpect(jsonPath("$.sugerenciaIa.decision").value("ACEPTADA"));

		cambiarTipo(1, "BACHE").andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("BACHE"))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value("CORREGIDA"))
				.andExpect(jsonPath("$.sugerenciaIa.tipoDecidido").value("BACHE"));

		assertThat(decisionEnBase(r).get("TIPO_DECIDIDO")).isEqualTo("BACHE");
		assertThat(jdbc.queryForObject("select count(*) from reclamo_sugerencia_ia where id_reclamo = ?", Integer.class,
				r.getId())).isEqualTo(1);
	}

	@Test
	void decidePersonalAdministrativoYQuienDecideSaleDelToken() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");

		cambiarTipo(1, "{\"tipo\":\"LUZ\",\"idUsuario\":" + vecinoA.getId() + "}", tokenAdministrativoA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sugerenciaIa.decididaPor").value("Admin Tivo"));

		assertThat(((Number) decisionEnBase(r).get("ID_USUARIO_DECISION")).longValue()).isEqualTo(administrativoA.getId());
	}

	// ---- Sin sugerencia, historial y estado

	@Test
	void sinSugerenciaCambiaElTipoYDejaConstanciaEnElHistorialSinTocarElEstado() throws Exception {
		Reclamo r = reclamo(1);
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		String nombreViejo = tipos.findByCodigo("BACHE").orElseThrow().getNombre();
		String nombreNuevo = tipos.findByCodigo("LUZ").orElseThrow().getNombre();

		cambiarTipo(1, "LUZ").andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("LUZ"))
				.andExpect(jsonPath("$.tipoNombre").value(nombreNuevo))
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.agente.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.sugerenciaIa.estado").value("SIN_ANALIZAR"))
				.andExpect(jsonPath("$.historial", hasSize(3)))
				.andExpect(jsonPath("$.historial[2].estadoAnterior").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[2].estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.historial[2].usuarioNombre").value("Admin Comuna"))
				.andExpect(jsonPath("$.historial[2].observacion").value("Cambió el tipo de " + nombreViejo + " a " + nombreNuevo))
				.andExpect(jsonPath("$.historial[2].agente").value(nullValue()))
				.andExpect(jsonPath("$.historial[2].agenteAnterior").value(nullValue()));
		assertThat(estadoEnBase(r)).isEqualTo("ASIGNADO");
	}

	@Test
	void elVecinoVeElTipoNuevoPeroNoLaFilaDelHistorial() throws Exception {
		Reclamo r = reclamo(1);
		cambiarTipo(1, "LUZ").andExpect(status().isOk());

		em.flush();
		em.clear();
		mockMvc.perform(get("/api/reclamos/mios/1").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("LUZ"))
				.andExpect(jsonPath("$.historial", hasSize(1)));
		assertThat(filasDeHistorial(r)).isEqualTo(2);
	}

	// "Último cambio de estado" (vecino y agente) no se mueve con un cambio de tipo (fila con el mismo estado).
	@Test
	void unCambioDeTipoNoMueveLaFechaDelUltimoCambioDeEstadoNiParaElVecinoNiParaElAgente() throws Exception {
		Reclamo r = reclamo(1);
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		em.flush();
		em.clear();
		String antesVecino = mockMvc.perform(get("/api/reclamos/mios").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String antesAgente = mockMvc.perform(get("/api/agente/reclamos").header("Authorization", "Bearer " + jwt.generar(agenteA)))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		String fechaVecino = com.jayway.jsonpath.JsonPath.read(antesVecino, "$.reclamos[0].estadoCambiadoEn");
		String fechaAgente = com.jayway.jsonpath.JsonPath.read(antesAgente, "$.reclamos[0].estadoCambiadoEn");
		esperar();

		cambiarTipo(1, "LUZ").andExpect(status().isOk());
		assertThat(filasDeHistorial(r)).isEqualTo(3);

		em.flush();
		em.clear();
		mockMvc.perform(get("/api/reclamos/mios").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
				.andExpect(jsonPath("$.reclamos[0].tipo").value("LUZ"))
				.andExpect(jsonPath("$.reclamos[0].estadoCambiadoEn").value(fechaVecino));
		mockMvc.perform(get("/api/agente/reclamos").header("Authorization", "Bearer " + jwt.generar(agenteA)))
				.andExpect(jsonPath("$.reclamos[0].estadoCambiadoEn").value(fechaAgente));
	}

	@Test
	void unaSugerenciaQueLlegaDespuesDelCambioQuedaPendienteDeDecision() throws Exception {
		Reclamo r = reclamo(1);
		cambiarTipo(1, "LUZ").andExpect(status().isOk());

		// La IA termina después de que el personal ya cambió el tipo.
		sugerirHecha(r, "BACHE");

		detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("LUZ"))
				.andExpect(jsonPath("$.sugerenciaIa.estado").value("HECHA"))
				.andExpect(jsonPath("$.sugerenciaIa.coincide").value(false))
				.andExpect(jsonPath("$.sugerenciaIa.decision").value(nullValue()));
		// Y se puede decidir: elegir el tipo que ya tiene el reclamo solo registra la decisión.
		cambiarTipo(1, "LUZ").andExpect(status().isOk())
				.andExpect(jsonPath("$.sugerenciaIa.decision").value("CORREGIDA"))
				.andExpect(jsonPath("$.historial", hasSize(2)));
	}

	// ---- Rechazos

	@Test
	void elMismoTipoSinSugerenciaPendienteDa409MismoTipo() throws Exception {
		Reclamo sinSugerencia = reclamo(1);
		Reclamo yaDecidida = reclamo(2);
		Reclamo conError = reclamo(3);
		sugerirHecha(yaDecidida, "LUZ");
		sugerencias.save(SugerenciaIa.conError(comunaA.getId(), conError.getId(), ProveedorIa.CLAUDE, "m", "falló", null, null));
		em.flush();

		cambiarTipo(1, "BACHE").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MISMO_TIPO"));
		cambiarTipo(3, "BACHE").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MISMO_TIPO"));
		// Con la sugerencia pendiente, mantener el tipo es una corrección (una vez); repetirla no suma nada.
		cambiarTipo(2, "BACHE").andExpect(status().isOk());
		cambiarTipo(2, "BACHE").andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("MISMO_TIPO"));

		assertThat(filasDeHistorial(sinSugerencia)).isEqualTo(1);
	}

	@Test
	void unReclamoResueltoODescartadoDa409ReclamoCerradoYNoTocaNada() throws Exception {
		Reclamo resuelto = reclamo(1);
		cambiar(resuelto, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(resuelto, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));
		Reclamo descartado = reclamo(2);
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));
		sugerirHecha(resuelto, "LUZ");

		for (int numero : new int[] { 1, 2 }) {
			cambiarTipo(numero, "LUZ").andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("RECLAMO_CERRADO"));
		}
		assertThat(tipoEnBase(resuelto)).isEqualTo("BACHE");
		assertThat(decisionEnBase(resuelto).get("DECISION")).isNull();
	}

	@Test
	void unTipoInexistenteDa400TipoInvalidoYVacioDa400Validacion() throws Exception {
		Reclamo r = reclamo(1);
		sugerirHecha(r, "LUZ");

		cambiarTipo(1, "NO_EXISTE").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("TIPO_INVALIDO"));
		cambiarTipo(1, "luz").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("TIPO_INVALIDO"));
		for (String cuerpo : List.of("{}", "{\"tipo\":null}", "{\"tipo\":\"\"}", "{\"tipo\":\"   \"}")) {
			cambiarTipo(1, cuerpo, tokenAdminA).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
		assertThat(tipoEnBase(r)).isEqualTo("BACHE");
		assertThat(decisionEnBase(r).get("DECISION")).isNull();
	}

	@Test
	void unReclamoDeOtraComunaOInexistenteDa404Identico() throws Exception {
		Reclamo deB = reclamo(comunaB, 7);

		String inexistente = cambiarTipo(8, "LUZ").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();
		String otraComuna = cambiarTipo(7, "LUZ").andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString();

		assertThat(sinRuta(otraComuna)).isEqualTo(sinRuta(inexistente));
		assertThat(tipoEnBase(deB)).isEqualTo("BACHE");
		assertThat(filasDeHistorial(deB)).isEqualTo(1);
	}

	@Test
	void vecinoAgenteYSuperadminRecibenSiempre403YSinTokenDa401() throws Exception {
		reclamo(1);
		// El superadmin es único en la base: dentro de esta transacción (que se deshace) se saca el que haya.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario superadmin = new Usuario();
		superadmin.setCorreo("m25-super@mail.com");
		superadmin.setNombre("Super");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setClaveHash("hash");
		superadmin.setActivo(true);
		usuarios.saveAndFlush(superadmin);
		for (String token : List.of(jwt.generar(vecinoA), jwt.generar(agenteA), jwt.generar(superadmin))) {
			cambiarTipo(1, "{\"tipo\":\"LUZ\"}", token).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			// 403 antes de validar el cuerpo.
			cambiarTipo(1, "{}", token).andExpect(status().isForbidden());
		}
		mockMvc.perform(put(RUTA + "/1/tipo").contentType("application/json").content("{\"tipo\":\"LUZ\"}"))
				.andExpect(status().isUnauthorized());
	}
}
