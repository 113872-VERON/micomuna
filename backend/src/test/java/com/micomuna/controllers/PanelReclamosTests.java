package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import com.micomuna.entities.ReclamoFoto;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.enums.OrigenFoto;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.models.CambioDeEstado;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoFotoRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;
import com.micomuna.security.UsuarioAutenticado;
import com.micomuna.services.CambioDeEstadoService;

import jakarta.persistence.EntityManager;

// MC-18 (subtareas MC-78 y MC-80): el panel de reclamos del personal. Contra la base de desarrollo, dentro de una
// transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PanelReclamosTests {

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
	private ReclamoFotoRepository fotos;

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
	private Usuario vecinoB;
	private Usuario agenteA;
	private Usuario otroAgenteA;
	private Usuario agenteB;
	private Usuario adminA;
	private String tokenAdminA;
	private String tokenAdministrativoA;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t18-panel-a");
		comunaB = comuna("t18-panel-b");
		vecinoA = usuario(comunaA, Rol.VECINO, "Vecina Ana", true);
		vecinoB = usuario(comunaB, Rol.VECINO, "Vecino Beto", true);
		agenteA = usuario(comunaA, Rol.AGENTE, "Álvaro Agente", true);
		otroAgenteA = usuario(comunaA, Rol.AGENTE, "Zoe Agente", true);
		agenteB = usuario(comunaB, Rol.AGENTE, "Agente de B", true);
		adminA = usuario(comunaA, Rol.ADMIN_COMUNA, "Admin Comuna", true);
		tokenAdminA = jwt.generar(adminA);
		tokenAdministrativoA = jwt.generar(usuario(comunaA, Rol.ADMINISTRATIVO, "Admin Tivo", true));
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
		u.setCorreo("p18-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(activo);
		return usuarios.saveAndFlush(u);
	}

	private Reclamo reclamo(Comuna comuna, Usuario vecino, int numero, String tipo, String direccion, String referencia,
			Instant creadoEn) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo(tipo).orElseThrow());
		r.setDescripcion("Reclamo " + numero);
		r.setDireccion(direccion);
		r.setReferencia(referencia);
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setCreadoEn(creadoEn);
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	private Reclamo reclamo(Comuna comuna, int numero, String direccion, String referencia) {
		return reclamo(comuna, comuna == comunaA ? vecinoA : vecinoB, numero, "BACHE", direccion, referencia,
				Instant.now().minus(30, ChronoUnit.DAYS).plus(numero, ChronoUnit.HOURS));
	}

	private Reclamo reclamo(int numero) {
		return reclamo(comunaA, numero, "Calle " + numero, null);
	}

	private ReclamoFoto foto(Reclamo reclamo, OrigenFoto origen, int orden, String tipo) {
		byte[] bytes = tipo.equals("image/png") ? ImagenesDePrueba.png(8 + orden, 8) : ImagenesDePrueba.jpeg(8 + orden, 8);
		return fotos.save(new ReclamoFoto(reclamo, origen, orden, bytes, tipo));
	}

	// Cambia de estado como el admin de la comuna A (el servicio toma la comuna del usuario autenticado).
	private void cambiar(Reclamo r, CambioDeEstado cambio) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(adminA.getId(), Rol.ADMIN_COMUNA, comunaA.getId()), null, List.of()));
		try {
			cambios.cambiar(r.getNumero(), cambio, adminA.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void asignar(Reclamo r) {
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
	}

	private ResultActions pedir(String ruta, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ruta)
				.header("Authorization", "Bearer " + token));
	}

	private ResultActions get(String ruta) throws Exception {
		return pedir(ruta, tokenAdminA);
	}


	// El texto va como parámetro de la petición (el cliente lo codifica): así los caracteres raros llegan tal cual.
	private ResultActions buscar(String texto) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(RUTA)
				.param("buscar", texto).header("Authorization", "Bearer " + tokenAdminA));
	}
	private ResultActions enviar(String ruta, String cuerpo, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(post(ruta).header("Authorization", "Bearer " + token)
				.contentType("application/json").content(cuerpo));
	}

	private ResultActions enviar(String ruta, String cuerpo) throws Exception {
		return enviar(ruta, cuerpo, tokenAdminA);
	}


	private static String sinRuta(String problema) {
		return problema.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	// ---- Acceso

	@Test
	void sinSesionResponde401() throws Exception {
		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(RUTA))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post(RUTA + "/descartes").contentType("application/json").content("{}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void vecinoAgenteYSuperadminRecibenSiempre403() throws Exception {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario superadmin = new Usuario();
		superadmin.setCorreo("root-18@mail.com");
		superadmin.setNombre("Root");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setClaveHash("hash");
		superadmin.setActivo(true);
		usuarios.saveAndFlush(superadmin);
		Reclamo r = reclamo(1);
		ReclamoFoto f = foto(r, OrigenFoto.VECINO, 1, "image/jpeg");

		for (String token : List.of(jwt.generar(vecinoA), jwt.generar(agenteA), jwt.generar(superadmin))) {
			for (String ruta : List.of(RUTA, RUTA + "/mapa", RUTA + "/resumen", RUTA + "/1", RUTA + "/1/fotos/" + f.getId(),
					"/api/panel/agentes")) {
				pedir(ruta, token).andExpect(status().isForbidden())
						.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			}
			// 403 antes de validar parámetros o cuerpo.
			pedir(RUTA + "?tamanio=0", token).andExpect(status().isForbidden());
			enviar(RUTA + "/cambios-de-estado", "{}", token).andExpect(status().isForbidden());
			enviar(RUTA + "/descartes", "{}", token).andExpect(status().isForbidden());
		}
	}

	@Test
	void administrativoYAdministradorPuedenEntrar() throws Exception {
		reclamo(1);
		for (String token : List.of(tokenAdminA, tokenAdministrativoA)) {
			pedir(RUTA, token).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
			pedir("/api/panel/agentes", token).andExpect(status().isOk());
		}
	}

	// ---- Lista: aislamiento, orden, paginación y validación

	@Test
	void laListaSoloTraeLosReclamosDeLaComunaDelToken() throws Exception {
		reclamo(1);
		reclamo(2);
		reclamo(comunaB, 1, "Calle de la B", null);

		get(RUTA).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(2, 1)))
				.andExpect(jsonPath("$.reclamos[0].pagina").doesNotExist());
	}

	@Test
	void laListaTraeLosDatosDeCadaFilaYLaPaginacionCorta() throws Exception {
		Reclamo r = reclamo(1);
		asignar(r);
		for (int i = 2; i <= 5; i++) {
			reclamo(i);
		}

		get(RUTA + "?pagina=1&tamanio=2")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(3, 2)))
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.tamanio").value(2));
		get(RUTA + "?pagina=2&tamanio=2").andExpect(jsonPath("$.reclamos[*].numero", contains(1)))
				.andExpect(jsonPath("$.reclamos[0].estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.reclamos[0].agente.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.reclamos[0].agente.nombre").value("Álvaro Agente"))
				.andExpect(jsonPath("$.reclamos[0].tipo").value("BACHE"))
				.andExpect(jsonPath("$.reclamos[0].tipoNombre").isNotEmpty())
				.andExpect(jsonPath("$.reclamos[0].direccion").value("Calle 1"))
				.andExpect(jsonPath("$.reclamos[0].cantidadDuplicados").value(0))
				.andExpect(jsonPath("$.reclamos[0].numeroOriginal").value(nullValue()));
		get(RUTA).andExpect(jsonPath("$.tamanio").value(25));
	}

	@Test
	void paginaTamanioOFiltrosInvalidosDan400() throws Exception {
		for (String consulta : List.of("?pagina=-1", "?pagina=1001", "?tamanio=0", "?tamanio=101", "?tipo=NOEXISTE",
				"?buscar=a", "?buscar=" + "x".repeat(101))) {
			get(RUTA + consulta).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
		get(RUTA + "?estado=CUALQUIERA").andExpect(status().isBadRequest());
		get(RUTA + "?tamanio=100&pagina=1000").andExpect(status().isOk());
		get(RUTA + "/mapa?buscar=a").andExpect(status().isBadRequest());
		get(RUTA + "/resumen?tipo=NOEXISTE").andExpect(status().isBadRequest());
	}

	@Test
	void lasListaNoTraeLasFotosNiCargaUnaEntidadPorFila() throws Exception {
		org.hibernate.stat.Statistics estadisticas = em.getEntityManagerFactory().unwrap(org.hibernate.SessionFactory.class)
				.getStatistics();
		estadisticas.setStatisticsEnabled(true);
		try {
			for (int i = 1; i <= 6; i++) {
				Reclamo r = reclamo(i);
				foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
				if (i % 2 == 0) {
					asignar(r);
				}
			}
			em.flush();
			em.clear();
			estadisticas.clear();

			get(RUTA + "?tamanio=6").andExpect(jsonPath("$.reclamos", hasSize(6)));

			// Una para la página, una para contar y la del usuario del token; no una más por cada fila.
			assertThat(estadisticas.getPrepareStatementCount()).as("consultas SQL").isLessThanOrEqualTo(3);
			assertThat(estadisticas.getEntityStatistics(ReclamoFoto.class.getName()).getLoadCount()).isZero();
			assertThat(estadisticas.getEntityStatistics(Reclamo.class.getName()).getLoadCount()).isZero();
		} finally {
			estadisticas.setStatisticsEnabled(false);
		}
	}

	// ---- Filtros

	@Test
	void filtraPorEstadoTipoYAgenteYSeCombinan() throws Exception {
		Reclamo r1 = reclamo(1);
		Reclamo r2 = reclamo(comunaA, vecinoA, 2, "LUZ", "Calle 2", null, Instant.now().minus(5, ChronoUnit.DAYS));
		Reclamo r3 = reclamo(3);
		reclamo(4);
		asignar(r1);
		asignar(r2);
		cambiar(r3, CambioDeEstado.asignar(otroAgenteA.getId()));

		get(RUTA + "?estado=ASIGNADO").andExpect(jsonPath("$.reclamos[*].numero", contains(2, 3, 1)));
		get(RUTA + "?estado=RECIBIDO").andExpect(jsonPath("$.reclamos[*].numero", contains(4)));
		get(RUTA + "?tipo=LUZ").andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
		get(RUTA + "?agente=" + agenteA.getId()).andExpect(jsonPath("$.reclamos[*].numero", contains(2, 1)));
		get(RUTA + "?agente=" + agenteA.getId() + "&tipo=BACHE").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		get(RUTA + "?agente=" + agenteA.getId() + "&estado=RECIBIDO").andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void unAgenteDeOtraComunaNoDaErrorNiEncuentraNada() throws Exception {
		asignar(reclamo(1));

		get(RUTA + "?agente=" + agenteB.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
		get(RUTA + "?agente=999999999").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
	}

	// ---- Búsqueda

	@Test
	void buscarSoloConDigitosEsElNumeroExacto() throws Exception {
		reclamo(5);
		reclamo(50);
		reclamo(15);
		reclamo(comunaB, 5, "Calle de la B", null);

		get(RUTA + "?buscar=5").andExpect(jsonPath("$.reclamos[*].numero", contains(5)));
		get(RUTA + "?buscar=050").andExpect(jsonPath("$.reclamos[*].numero", contains(50)));
		get(RUTA + "?buscar=999").andExpect(jsonPath("$.total").value(0));
		// Un número demasiado grande para ser un reclamo: no encuentra nada (y no falla).
		get(RUTA + "?buscar=99999999999999999999").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void buscarTextoEnDireccionOReferenciaSinMayusculasNiTildes() throws Exception {
		reclamo(comunaA, 1, "Av. San Martín 123", "Frente a la plaza");
		reclamo(comunaA, 2, "Calle Perón 50", "Esquina del Almacén");
		reclamo(comunaA, 3, null, null);

		get(RUTA + "?buscar=martin").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		get(RUTA + "?buscar=MARTÍN").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		buscar("martín")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		buscar("peron")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
		get(RUTA + "?buscar=almacen").andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
		buscar("ALMACÉN")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
		// En la referencia.
		get(RUTA + "?buscar=plaza").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		get(RUTA + "?buscar=inexistente").andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void buscarConCaracteresRarosNoRompeNiActuaComoComodin() throws Exception {
		reclamo(comunaA, 1, "Ruta 20 km 100% asfaltada", null);
		reclamo(comunaA, 2, "O'Higgins 12", "casa_roja");
		reclamo(comunaA, 3, "Casa Aroja", null);
		reclamo(comunaA, 4, "Calle comun", null);
		reclamo(comunaA, 5, "Pasaje a!b", null);

		// % y _ se buscan como letras comunes, no como comodines.
		buscar("100%")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		buscar("%%").andExpect(jsonPath("$.total").value(0));
		buscar("x_r").andExpect(jsonPath("$.total").value(0));
		buscar("casa_roja")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
		// Comilla, el carácter de escape que usa la consulta y otros símbolos: nada de errores ni de inyección.
		buscar("o'hig")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
		buscar("a!b")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(5)));
		for (String raro : List.of("' or '1'='1", "'; drop table reclamo; --", "\\\\", "\"\"", "x' escape '", "%_!")) {
			buscar(raro).andExpect(status().isOk())
					.andExpect(jsonPath("$.total").value(0));
		}
		buscar("' or '1'='1")
				.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void laBusquedaSeCombinaConLosOtrosFiltrosYAplicaAlMapaYAlResumen() throws Exception {
		Reclamo r1 = reclamo(comunaA, 1, "Calle Sol", null);
		reclamo(comunaA, 2, "Calle Sol", null);
		reclamo(comunaA, 3, "Calle Luna", null);
		asignar(r1);

		get(RUTA + "?buscar=sol&estado=ASIGNADO").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		get(RUTA + "/mapa?buscar=sol").andExpect(jsonPath("$.puntos[*].numero", contains(2, 1)));
		get(RUTA + "/resumen?buscar=sol").andExpect(jsonPath("$.total").value(2));
	}

	// ---- Resumen

	@Test
	void elResumenCuentaPorEstadoConLosFiltrosSalvoElDeEstadoYElSinAsignarNoTieneFiltros() throws Exception {
		Instant viejo = Instant.now().minus(40, ChronoUnit.DAYS);
		reclamo(comunaA, vecinoA, 1, "BACHE", "Calle 1", null, viejo);
		reclamo(comunaA, vecinoA, 2, "LUZ", "Calle 2", null, viejo.plus(1, ChronoUnit.DAYS));
		Reclamo r3 = reclamo(3);
		Reclamo r4 = reclamo(comunaA, vecinoA, 4, "LUZ", "Calle 4", null, Instant.now().minus(3, ChronoUnit.DAYS));
		asignar(r3);
		asignar(r4);
		cambiar(r4, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		reclamo(comunaB, 1, "Otra comuna", null);

		get(RUTA + "/resumen")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.porEstado.RECIBIDO").value(2))
				.andExpect(jsonPath("$.porEstado.ASIGNADO").value(1))
				.andExpect(jsonPath("$.porEstado.EN_REPARACION").value(1))
				.andExpect(jsonPath("$.porEstado.RESUELTO").value(0))
				.andExpect(jsonPath("$.porEstado.DESCARTADO").value(0))
				.andExpect(jsonPath("$.total").value(4))
				.andExpect(jsonPath("$.sinAsignar").value(2))
				.andExpect(jsonPath("$.sinAsignarMasAntiguo").isNotEmpty());

		// Con el filtro de tipo cuenta menos, pero "sin asignar" sigue siendo el de toda la comuna.
		get(RUTA + "/resumen?tipo=LUZ")
				.andExpect(jsonPath("$.porEstado.RECIBIDO").value(1))
				.andExpect(jsonPath("$.porEstado.EN_REPARACION").value(1))
				.andExpect(jsonPath("$.porEstado.ASIGNADO").value(0))
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.sinAsignar").value(2));
		// El filtro de estado se ignora: el desplegable necesita saber cuántos hay en cada uno.
		get(RUTA + "/resumen?agente=" + agenteA.getId()).andExpect(jsonPath("$.total").value(2));
	}

	@Test
	void sinReclamosSinAsignarElResumenDaCeroYFechaVacia() throws Exception {
		get(RUTA + "/resumen").andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.sinAsignar").value(0))
				.andExpect(jsonPath("$.sinAsignarMasAntiguo").value(nullValue()))
				.andExpect(jsonPath("$.porEstado.RECIBIDO").value(0));
	}

	@Test
	void elSinAsignarMasAntiguoEsLaFechaDelRecibidoMasViejo() throws Exception {
		Instant viejo = Instant.now().minus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
		reclamo(comunaA, vecinoA, 1, "BACHE", "Calle 1", null, viejo);
		reclamo(2);
		Reclamo asignadoMasViejo = reclamo(comunaA, vecinoA, 3, "BACHE", "Calle 3", null, viejo.minus(10, ChronoUnit.DAYS));
		asignar(asignadoMasViejo);

		String cuerpo = get(RUTA + "/resumen").andReturn().getResponse().getContentAsString();
		Instant fecha = Instant.parse(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.sinAsignarMasAntiguo"));
		assertThat(fecha).isBetween(viejo.minusSeconds(1), viejo.plusSeconds(1));
	}

	// ---- Mapa

	@Test
	void elMapaTrae1000PuntosComoMaximoLosMasNuevosYMarcaElRecorte() throws Exception {
		Instant base = Instant.now().minus(100, ChronoUnit.DAYS);
		insertarMuchos(1001, base);

		String cuerpo = get(RUTA + "/mapa").andExpect(status().isOk()).andExpect(jsonPath("$.puntos", hasSize(1000)))
				.andExpect(jsonPath("$.recortado").value(true)).andReturn().getResponse().getContentAsString();
		// Los más nuevos: falta justamente el más viejo (el número 1).
		List<Integer> numeros = com.jayway.jsonpath.JsonPath.read(cuerpo, "$.puntos[*].numero");
		assertThat(numeros).doesNotContain(1).contains(1001);
	}

	@Test
	void conExactamente1000NoSeMarcaRecorteYElPuntoTraeSoloLoNecesario() throws Exception {
		insertarMuchos(1000, Instant.now().minus(100, ChronoUnit.DAYS));

		get(RUTA + "/mapa").andExpect(jsonPath("$.puntos", hasSize(1000))).andExpect(jsonPath("$.recortado").value(false))
				.andExpect(jsonPath("$.puntos[0].estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.puntos[0].tipo").value("BACHE"))
				.andExpect(jsonPath("$.puntos[0].latitud").value(-31.42))
				.andExpect(jsonPath("$.puntos[0].longitud").value(-64.18))
				.andExpect(jsonPath("$.puntos[0].descripcion").doesNotExist())
				.andExpect(jsonPath("$.puntos[0].direccion").doesNotExist());
	}

	@Test
	void elMapaAplicaLosFiltrosYSoloTraeLaComunaDelToken() throws Exception {
		Reclamo r1 = reclamo(1);
		reclamo(2);
		reclamo(comunaB, 1, "Otra comuna", null);
		asignar(r1);

		get(RUTA + "/mapa").andExpect(jsonPath("$.puntos[*].numero", contains(2, 1)))
				.andExpect(jsonPath("$.recortado").value(false));
		get(RUTA + "/mapa?estado=ASIGNADO").andExpect(jsonPath("$.puntos[*].numero", contains(1)));
		get(RUTA + "/mapa?agente=" + agenteA.getId()).andExpect(jsonPath("$.puntos[*].numero", contains(1)));
	}

	// Inserta reclamos directo por SQL (con 6 decimales de ubicación) para no crear mil entidades.
	private void insertarMuchos(int cantidad, Instant base) {
		em.flush();
		Long idTipo = tipos.findByCodigo("BACHE").orElseThrow().getId();
		List<Object[]> filas = new ArrayList<>();
		for (int i = 1; i <= cantidad; i++) {
			java.sql.Timestamp fecha = java.sql.Timestamp.from(base.plus(i, ChronoUnit.MINUTES));
			filas.add(new Object[] { comunaA.getId(), i, vecinoA.getId(), idTipo, "Reclamo " + i, "RECIBIDO", -31.42, -64.18,
					fecha, fecha });
		}
		jdbc.batchUpdate("insert into reclamo (id_comuna, numero, id_vecino, id_tipo, descripcion, estado, latitud, "
				+ "longitud, creado_en, actualizado_en) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", filas);
	}

	// ---- Detalle

	@Test
	void elDetalleDelPanelTraeVecinoAgenteHistorialConQuienYNotaYLosDuplicados() throws Exception {
		Reclamo original = reclamo(1);
		foto(original, OrigenFoto.VECINO, 1, "image/jpeg");
		cambiar(original, new CambioDeEstado(EstadoReclamo.ASIGNADO, null, null, null, null, "nota interna", agenteA.getId()));
		Reclamo duplicado = reclamo(2);
		cambiar(duplicado, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, 1));
		Reclamo otroDuplicado = reclamo(3);
		cambiar(otroDuplicado, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, 1));

		String cuerpo = get(RUTA + "/1").andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.vecinoNombre").value("Vecina Ana"))
				.andExpect(jsonPath("$.agente.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.agente.nombre").value("Álvaro Agente"))
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.duplicados", contains(2, 3)))
				.andExpect(jsonPath("$.fotos", hasSize(1)))
				.andExpect(jsonPath("$.historial[*].estado", contains("RECIBIDO", "ASIGNADO")))
				.andExpect(jsonPath("$.historial[0].estadoAnterior").value(nullValue()))
				.andExpect(jsonPath("$.historial[0].usuarioNombre").value("Vecina Ana"))
				.andExpect(jsonPath("$.historial[1].estadoAnterior").value("RECIBIDO"))
				.andExpect(jsonPath("$.historial[1].usuarioNombre").value("Admin Comuna"))
				.andExpect(jsonPath("$.historial[1].observacion").value("nota interna"))
				.andExpect(jsonPath("$.descarte").value(nullValue()))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue()))
				.andReturn().getResponse().getContentAsString();
		// Del vecino solo el nombre: ni su correo ni su id.
		assertThat(cuerpo).doesNotContain(vecinoA.getCorreo()).doesNotContain("claveHash");

		get(RUTA + "/2").andExpect(jsonPath("$.estado").value("DESCARTADO"))
				.andExpect(jsonPath("$.descarte.motivo").value("DUPLICADO"))
				.andExpect(jsonPath("$.descarte.observacion").value(nullValue()))
				.andExpect(jsonPath("$.descarte.numeroOriginal").value(1))
				.andExpect(jsonPath("$.duplicados", hasSize(0)));
		get(RUTA).andExpect(jsonPath("$.reclamos[?(@.numero==2)].numeroOriginal", contains(1)))
				.andExpect(jsonPath("$.reclamos[?(@.numero==1)].cantidadDuplicados", contains(2)));
	}

	@Test
	void elDetalleDeUnResueltoTraeLoRealizadoYLasFotosDelTrabajoYAntesDeResolverNo() throws Exception {
		Reclamo r = reclamo(1);
		foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		ReclamoFoto trabajo = foto(r, OrigenFoto.TRABAJO, 1, "image/png");
		asignar(r);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		get(RUTA + "/1").andExpect(jsonPath("$.fotos[*].origen", contains("VECINO")))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue()));
		get(RUTA + "/1/fotos/" + trabajo.getId()).andExpect(status().isNotFound());

		cambiar(r, CambioDeEstado.resolver("Se arregló"));
		get(RUTA + "/1").andExpect(jsonPath("$.fotos[*].origen", contains("VECINO", "TRABAJO")))
				.andExpect(jsonPath("$.trabajoRealizado").value("Se arregló"));
		get(RUTA + "/1/fotos/" + trabajo.getId()).andExpect(status().isOk());
	}

	@Test
	void unReclamoDeOtraComunaOInexistenteDan404IdenticoEnDetalleYFoto() throws Exception {
		Reclamo deB = reclamo(comunaB, 7, "Calle de la B", null);
		ReclamoFoto fotoB = foto(deB, OrigenFoto.VECINO, 1, "image/jpeg");
		Reclamo propio = reclamo(1);
		ReclamoFoto fotoPropia = foto(propio, OrigenFoto.VECINO, 1, "image/jpeg");

		String inexistente = get(RUTA + "/8").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();
		String deOtraComuna = get(RUTA + "/7").andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString();
		assertThat(sinRuta(deOtraComuna)).isEqualTo(sinRuta(inexistente));

		String fotoInexistente = get(RUTA + "/1/fotos/99999999").andExpect(status().isNotFound()).andReturn()
				.getResponse().getContentAsString();
		// La foto existe, pero es de la otra comuna (pedida con su número o con uno propio).
		assertThat(sinRuta(get(RUTA + "/7/fotos/" + fotoB.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString())).isEqualTo(sinRuta(fotoInexistente));
		assertThat(sinRuta(get(RUTA + "/1/fotos/" + fotoB.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString())).isEqualTo(sinRuta(fotoInexistente));
		// Y la foto tiene que ser del reclamo pedido.
		reclamo(2);
		get(RUTA + "/2/fotos/" + fotoPropia.getId()).andExpect(status().isNotFound());
	}

	@Test
	void laFotoDelPanelTieneLasCabecerasDeSeguridad() throws Exception {
		Reclamo r = reclamo(1);
		ReclamoFoto jpeg = foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		ReclamoFoto png = foto(r, OrigenFoto.VECINO, 2, "image/png");

		get(RUTA + "/1/fotos/" + jpeg.getId()).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().doesNotExist("Content-Disposition"))
				.andExpect(content().bytes(jpeg.getImagen()));
		get(RUTA + "/1/fotos/" + png.getId()).andExpect(header().string("Content-Type", "image/png"));
	}

	// ---- Agentes

	@Test
	void losAgentesSonLosActivosConRolAgenteDeLaComunaPorNombre() throws Exception {
		usuario(comunaA, Rol.AGENTE, "Inactivo Agente", false);
		usuario(comunaA, Rol.VECINO, "Un Vecino", true);
		usuario(comunaA, Rol.ADMINISTRATIVO, "Un Administrativo", true);
		usuario(comunaA, Rol.AGENTE, "bruno minusculas", true);

		String cuerpo = get("/api/panel/agentes").andExpect(status().isOk())
				.andExpect(jsonPath("$[*].nombre", contains("Álvaro Agente", "bruno minusculas", "Zoe Agente")))
				.andExpect(jsonPath("$[0].id").value(agenteA.getId().intValue()))
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("mail.com").doesNotContain(agenteB.getNombre());
	}

	// ---- Lotes: cambios de estado

	private static String numeros(Integer... numeros) {
		return java.util.Arrays.toString(numeros);
	}

	@Test
	void elLoteDeCambiosAplicaLosQueSePuedenYInformaLosOmitidosConSuMotivo() throws Exception {
		Reclamo recibido = reclamo(1);
		Reclamo yaAsignado = reclamo(2);
		asignar(yaAsignado);
		Reclamo resuelto = reclamo(3);
		asignar(resuelto);
		cambiar(resuelto, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));
		reclamo(comunaB, 7, "De la otra comuna", null);

		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":" + numeros(1, 2, 3, 7, 8) + ",\"estado\":\"ASIGNADO\",\"idAgente\":"
				+ otroAgenteA.getId() + "}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aplicados", contains(1)))
				.andExpect(jsonPath("$.omitidos", hasSize(4)))
				.andExpect(jsonPath("$.omitidos[0].numero").value(2))
				.andExpect(jsonPath("$.omitidos[0].codigo").value("YA_ESTABA_EN_ESE_ESTADO"))
				.andExpect(jsonPath("$.omitidos[0].motivo").value("El n.º 2 ya está asignado a Álvaro Agente."))
				.andExpect(jsonPath("$.omitidos[1].numero").value(3))
				.andExpect(jsonPath("$.omitidos[1].codigo").value("TRANSICION_INVALIDA"))
				.andExpect(jsonPath("$.omitidos[1].motivo").value("El n.º 3 no puede pasar de «Resuelto» a «Asignado»."))
				.andExpect(jsonPath("$.omitidos[2].codigo").value("NO_ENCONTRADO"))
				.andExpect(jsonPath("$.omitidos[3].codigo").value("NO_ENCONTRADO"))
				.andExpect(jsonPath("$.omitidos[3].motivo").value("No encontramos el n.º 8."));

		// Lo aplicado quedó con el agente, el historial y quién lo hizo; lo omitido no se tocó.
		get(RUTA + "/1").andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.agente.id").value(otroAgenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial[1].usuarioNombre").value("Admin Comuna"));
		get(RUTA + "/2").andExpect(jsonPath("$.agente.id").value(agenteA.getId().intValue()))
				.andExpect(jsonPath("$.historial", hasSize(2)));
		get(RUTA + "/3").andExpect(jsonPath("$.estado").value("RESUELTO"));
	}

	@Test
	void unNumeroDeOtraComunaEnUnLoteSeInformaIgualQueUnoInexistente() throws Exception {
		Reclamo deB = reclamo(comunaB, 7, "De la otra comuna", null);

		String cuerpo = "{\"numeros\":[7,8],\"estado\":\"RECIBIDO\"}";
		String resultado = enviar(RUTA + "/cambios-de-estado", cuerpo).andExpect(status().isOk())
				.andExpect(jsonPath("$.aplicados", hasSize(0)))
				.andReturn().getResponse().getContentAsString();
		assertThat(resultado.replace("7", "N").replace("8", "N")).contains("NO_ENCONTRADO");
		get(RUTA + "/mapa").andExpect(jsonPath("$.puntos", hasSize(0)));
		assertThat(jdbc.queryForObject("select estado from reclamo where id = ?", String.class, deB.getId()))
				.isEqualTo("RECIBIDO");
		assertThat(jdbc.queryForObject("select count(*) from reclamo_estado where id_reclamo = ?", Integer.class,
				deB.getId())).isEqualTo(1);
	}

	@Test
	void volverARecibidoQuitaElAgente() throws Exception {
		Reclamo r = reclamo(1);
		asignar(r);

		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"RECIBIDO\"}")
				.andExpect(jsonPath("$.aplicados", contains(1)));

		get(RUTA + "/1").andExpect(jsonPath("$.estado").value("RECIBIDO")).andExpect(jsonPath("$.agente").value(nullValue()));
	}

	@Test
	void asignarConAgenteInvalidoDa400SinTocarNada() throws Exception {
		Reclamo r = reclamo(1);
		Usuario inactivo = usuario(comunaA, Rol.AGENTE, "Inactivo", false);
		Usuario vecino = usuario(comunaA, Rol.VECINO, "No agente", true);

		for (String idAgente : List.of("null", String.valueOf(agenteB.getId()), String.valueOf(inactivo.getId()),
				String.valueOf(vecino.getId()), String.valueOf(adminA.getId()), "999999999")) {
			enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"ASIGNADO\",\"idAgente\":" + idAgente + "}")
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("AGENTE_INVALIDO"));
		}
		// Un agente con un estado que no es ASIGNADO tampoco se acepta.
		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"RECIBIDO\",\"idAgente\":" + agenteA.getId() + "}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("AGENTE_INVALIDO"));
		assertThat(jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId()))
				.isEqualTo("RECIBIDO");
		assertThat(jdbc.queryForObject("select count(*) from reclamo_estado where id_reclamo = ?", Integer.class, r.getId()))
				.isEqualTo(1);
	}

	@Test
	void pedidosInvalidosDeCambiosDeEstadoDan400() throws Exception {
		reclamo(1);
		List<String> invalidos = new ArrayList<>(List.of(
				"{\"numeros\":[],\"estado\":\"RECIBIDO\"}",
				"{\"estado\":\"RECIBIDO\"}",
				"{\"numeros\":[1,1],\"estado\":\"RECIBIDO\"}",
				"{\"numeros\":[0],\"estado\":\"RECIBIDO\"}",
				"{\"numeros\":[1]}",
				"{\"numeros\":[1],\"estado\":\"DESCARTADO\"}",
				"{\"numeros\":[" + java.util.stream.IntStream.rangeClosed(1, 101).boxed().map(String::valueOf)
						.collect(java.util.stream.Collectors.joining(",")) + "],\"estado\":\"RECIBIDO\"}"));
		for (String cuerpo : invalidos) {
			enviar(RUTA + "/cambios-de-estado", cuerpo).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"INVENTADO\"}").andExpect(status().isBadRequest());
	}

	@Test
	void el100DeNumerosEsElMaximoPermitido() throws Exception {
		String numeros = java.util.stream.IntStream.rangeClosed(1, 100).boxed().map(String::valueOf)
				.collect(java.util.stream.Collectors.joining(","));

		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[" + numeros + "],\"estado\":\"RECIBIDO\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.omitidos", hasSize(100)));
	}

	// ---- Lotes: descartes

	@Test
	void elLoteDeDescartesDescartaYOmiteLosCerrados() throws Exception {
		Reclamo recibido = reclamo(1);
		Reclamo asignado = reclamo(2);
		asignar(asignado);
		Reclamo resuelto = reclamo(3);
		asignar(resuelto);
		cambiar(resuelto, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));
		Reclamo descartado = reclamo(4);
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "Ya descartado", null));
		Reclamo enReparacion = reclamo(5);
		asignar(enReparacion);
		cambiar(enReparacion, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		enviar(RUTA + "/descartes", "{\"numeros\":" + numeros(1, 2, 3, 4, 5, 9)
				+ ",\"motivo\":\"NO_ES_DE_LA_COMUNA\",\"observacion\":\"  Es de la ruta provincial  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aplicados", contains(1, 2)))
				.andExpect(jsonPath("$.omitidos[0].numero").value(3))
				.andExpect(jsonPath("$.omitidos[0].codigo").value("YA_CERRADO"))
				.andExpect(jsonPath("$.omitidos[0].motivo").value("El n.º 3 ya está cerrado."))
				.andExpect(jsonPath("$.omitidos[1].codigo").value("YA_CERRADO"))
				.andExpect(jsonPath("$.omitidos[2].numero").value(5))
				.andExpect(jsonPath("$.omitidos[2].codigo").value("TRANSICION_INVALIDA"))
				.andExpect(jsonPath("$.omitidos[3].codigo").value("NO_ENCONTRADO"));

		get(RUTA + "/1").andExpect(jsonPath("$.estado").value("DESCARTADO"))
				.andExpect(jsonPath("$.descarte.motivo").value("NO_ES_DE_LA_COMUNA"))
				.andExpect(jsonPath("$.descarte.observacion").value("Es de la ruta provincial"));
		get(RUTA + "/3").andExpect(jsonPath("$.estado").value("RESUELTO"));
	}

	@Test
	void descartarComoDuplicadoVinculaConElOriginal() throws Exception {
		reclamo(1);
		reclamo(2);
		reclamo(3);

		enviar(RUTA + "/descartes", "{\"numeros\":[2,3],\"motivo\":\"DUPLICADO\",\"numeroOriginal\":1}")
				.andExpect(jsonPath("$.aplicados", contains(2, 3)));

		get(RUTA + "/1").andExpect(jsonPath("$.duplicados", contains(2, 3))).andExpect(jsonPath("$.estado").value("RECIBIDO"));
		get(RUTA + "/2").andExpect(jsonPath("$.descarte.numeroOriginal").value(1))
				.andExpect(jsonPath("$.descarte.observacion").value(nullValue()));
	}

	@Test
	void elOriginalSeValidaUnaVezAlPrincipio() throws Exception {
		reclamo(1);
		reclamo(2);
		Reclamo descartado = reclamo(3);
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "Ya", null));
		reclamo(comunaB, 9, "Otra comuna", null);

		// Inexistente, de otra comuna, descartado, incluido en la lista (o el propio): 400 y no se descarta nada.
		for (String original : List.of("99", "9", "3")) {
			enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"DUPLICADO\",\"numeroOriginal\":" + original + "}")
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ORIGINAL_INVALIDO"));
		}
		enviar(RUTA + "/descartes", "{\"numeros\":[1,2],\"motivo\":\"DUPLICADO\",\"numeroOriginal\":1}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ORIGINAL_INVALIDO"));
		enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"DUPLICADO\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ORIGINAL_REQUERIDO"));
		enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"OTRO\",\"observacion\":\"x\",\"numeroOriginal\":1}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("ORIGINAL_INVALIDO"));
		get(RUTA + "/2").andExpect(jsonPath("$.estado").value("RECIBIDO"));
	}

	@Test
	void unDuplicadoNoAceptaObservacionYNoSeDescartaNada() throws Exception {
		reclamo(1);
		reclamo(2);

		enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"DUPLICADO\",\"numeroOriginal\":1,\"observacion\":\"Mismo pozo\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
		get(RUTA + "/2").andExpect(jsonPath("$.estado").value("RECIBIDO"));
	}

	@Test
	void laObservacionEsObligatoriaSalvoConDuplicadoYTieneTopeDe300() throws Exception {
		reclamo(1);
		reclamo(2);

		enviar(RUTA + "/descartes", "{\"numeros\":[2]}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("MOTIVO_REQUERIDO"));
		for (String motivo : List.of("OTRO", "NO_ES_DE_LA_COMUNA", "NO_SE_PUEDE_ATENDER")) {
			enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"" + motivo + "\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("OBSERVACION_REQUERIDA"));
			enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"" + motivo + "\",\"observacion\":\"   \"}")
					.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("OBSERVACION_REQUERIDA"));
		}
		enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"OTRO\",\"observacion\":\"" + "x".repeat(301) + "\"}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
		enviar(RUTA + "/descartes", "{\"numeros\":[2],\"motivo\":\"OTRO\",\"observacion\":\"" + "x".repeat(300) + "\"}")
				.andExpect(status().isOk()).andExpect(jsonPath("$.aplicados", contains(2)));
		get(RUTA + "/2").andExpect(jsonPath("$.estado").value("DESCARTADO"));
	}

	@Test
	void pedidosInvalidosDeDescartesDan400() throws Exception {
		reclamo(1);
		for (String cuerpo : List.of("{\"numeros\":[],\"motivo\":\"OTRO\",\"observacion\":\"x\"}",
				"{\"motivo\":\"OTRO\",\"observacion\":\"x\"}",
				"{\"numeros\":[1,1],\"motivo\":\"OTRO\",\"observacion\":\"x\"}",
				"{\"numeros\":[-1],\"motivo\":\"OTRO\",\"observacion\":\"x\"}")) {
			enviar(RUTA + "/descartes", cuerpo).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
		enviar(RUTA + "/descartes", "{\"numeros\":[1],\"motivo\":\"INVENTADO\"}").andExpect(status().isBadRequest());
	}

	@Test
	void unNumeroDeOtraComunaEnUnLoteDeDescartesEsNoEncontradoYNoSeToca() throws Exception {
		Reclamo deB = reclamo(comunaB, 7, "De la otra comuna", null);

		enviar(RUTA + "/descartes", "{\"numeros\":[7],\"motivo\":\"OTRO\",\"observacion\":\"x\"}")
				.andExpect(jsonPath("$.aplicados", hasSize(0))).andExpect(jsonPath("$.omitidos[0].codigo").value("NO_ENCONTRADO"));
		assertThat(jdbc.queryForObject("select estado from reclamo where id = ?", String.class, deB.getId()))
				.isEqualTo("RECIBIDO");
	}

	@Test
	void quienHaceElCambioSaleDelTokenAunqueElPedidoTraigaOtroCampo() throws Exception {
		reclamo(1);

		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"ASIGNADO\",\"idAgente\":" + agenteA.getId()
				+ ",\"idUsuario\":" + vecinoA.getId() + "}", tokenAdministrativoA).andExpect(status().isOk());

		get(RUTA + "/1").andExpect(jsonPath("$.historial[1].usuarioNombre").value("Admin Tivo"));
	}

	@Test
	void asignarUnoYaAsignadoAlMismoAgenteDiceQueYaEstabaEnEseEstado() throws Exception {
		asignar(reclamo(1));

		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"ASIGNADO\",\"idAgente\":" + agenteA.getId() + "}")
				.andExpect(jsonPath("$.aplicados", hasSize(0)))
				.andExpect(jsonPath("$.omitidos[0].codigo").value("YA_ESTABA_EN_ESE_ESTADO"))
				.andExpect(jsonPath("$.omitidos[0].motivo").value("El n.º 1 ya estaba en ese estado."));
		get(RUTA + "/1").andExpect(jsonPath("$.agente.id").value(agenteA.getId().intValue()));
	}

	@Test
	void unAgenteQueNoAceptoLaInvitacionNoApareceNiSePuedeAsignar() throws Exception {
		Usuario pendiente = usuario(comunaA, Rol.AGENTE, "Ana Agente", true);
		pendiente.setClaveHash(null);
		usuarios.saveAndFlush(pendiente);
		reclamo(1);

		get("/api/panel/agentes").andExpect(jsonPath("$[*].nombre", contains("Álvaro Agente", "Zoe Agente")));
		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[1],\"estado\":\"ASIGNADO\",\"idAgente\":" + pendiente.getId() + "}")
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("AGENTE_INVALIDO"));
	}

	@Test
	void laEniaCuentaComoNEnLaBusqueda() throws Exception {
		reclamo(comunaA, 1, "Calle Peñaloza 10", null);
		reclamo(comunaA, 2, "Calle Penalosa 10", null);

		buscar("penaloza").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		buscar("PEÑALOZA").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		buscar("peñaloza").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		buscar("penalosa").andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
	}

	@Test
	void descartarQuitaElAgente() throws Exception {
		asignar(reclamo(1));

		enviar(RUTA + "/descartes", "{\"numeros\":[1],\"motivo\":\"OTRO\",\"observacion\":\"No corresponde\"}")
				.andExpect(jsonPath("$.aplicados", contains(1)));

		get(RUTA + "/1").andExpect(jsonPath("$.estado").value("DESCARTADO")).andExpect(jsonPath("$.agente").value(nullValue()));
	}

	@Test
	void elLoteSeAplicaEnElOrdenPedido() throws Exception {
		reclamo(1);
		reclamo(2);
		reclamo(3);

		enviar(RUTA + "/cambios-de-estado", "{\"numeros\":[3,1,2],\"estado\":\"ASIGNADO\",\"idAgente\":" + agenteA.getId() + "}")
				.andExpect(jsonPath("$.aplicados", contains(3, 1, 2)));
		get(RUTA + "?estado=ASIGNADO").andExpect(jsonPath("$.reclamos[*].numero", containsInAnyOrder(1, 2, 3)));
	}
}
