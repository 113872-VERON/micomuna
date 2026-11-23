package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

// MC-17 (subtareas MC-63 y MC-65): el vecino ve sus reclamos, el detalle con su historial y las fotos.
// Contra la base de desarrollo, dentro de una transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MisReclamosTests {

	private static final String RUTA = "/api/reclamos/mios";

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

	private Comuna comunaA;
	private Comuna comunaB;
	private Usuario vecinoA;
	private Usuario otroVecinoA;
	private Usuario vecinoB;
	private Usuario agenteA;
	private String tokenVecinoA;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t17-mios-a");
		comunaB = comuna("t17-mios-b");
		vecinoA = usuario(comunaA, Rol.VECINO);
		otroVecinoA = usuario(comunaA, Rol.VECINO);
		vecinoB = usuario(comunaB, Rol.VECINO);
		agenteA = usuario(comunaA, Rol.AGENTE);
		tokenVecinoA = jwt.generar(vecinoA);
	}

	// ---- Ayudas

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo("u17-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Un reclamo con su fila de alta en el historial.
	private Reclamo reclamo(Comuna comuna, Usuario vecino, int numero, Instant creadoEn) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Pozo número " + numero);
		r.setReferencia("Frente al kiosco");
		r.setDireccion("Calle falsa 123");
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setCreadoEn(creadoEn);
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	private Reclamo reclamo(Comuna comuna, Usuario vecino, int numero) {
		return reclamo(comuna, vecino, numero, Instant.now().minus(1, ChronoUnit.DAYS));
	}

	private ReclamoFoto foto(Reclamo reclamo, OrigenFoto origen, int orden, String tipo) {
		byte[] bytes = tipo.equals("image/png") ? ImagenesDePrueba.png(8 + orden, 8) : ImagenesDePrueba.jpeg(8 + orden, 8);
		return fotos.save(new ReclamoFoto(reclamo, origen, orden, bytes, tipo));
	}

	// Cambia de estado como el agente de la comuna A (el servicio toma la comuna del usuario autenticado).
	private void cambiar(Reclamo r, CambioDeEstado cambio) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(agenteA.getId(), Rol.AGENTE, comunaA.getId()), null, List.of()));
		try {
			// ASIGNADO exige agente: si el pedido no lo trae, se asigna al agente de la prueba.
			CambioDeEstado completo = cambio.nuevo() == EstadoReclamo.ASIGNADO && cambio.idAgente() == null
					? CambioDeEstado.asignar(agenteA.getId()) : cambio;
			cambios.cambiar(r.getNumero(), completo, agenteA.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private ResultActions pedir(String ruta, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(ruta).header("Authorization", "Bearer " + token));
	}

	private ResultActions pedir(String ruta) throws Exception {
		return pedir(ruta, tokenVecinoA);
	}

	// El 404 es el mismo para todos salvo por la ruta pedida (campo "instance"): se compara el resto.
	private static String sinRuta(String problema) {
		return problema.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	// ---- Acceso

	@Test
	void sinSesionResponde401() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		mockMvc.perform(get(RUTA + "/1")).andExpect(status().isUnauthorized());
		mockMvc.perform(get(RUTA + "/1/fotos/1")).andExpect(status().isUnauthorized());
	}

	@Test
	void cualquierRolQueNoSeaVecinoResponde403EnLosTresEndpoints() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		ReclamoFoto f = foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		for (Rol rol : List.of(Rol.AGENTE, Rol.ADMINISTRATIVO, Rol.ADMIN_COMUNA)) {
			String token = jwt.generar(usuario(comunaA, rol));
			pedir(RUTA, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			pedir(RUTA + "/1", token).andExpect(status().isForbidden());
			pedir(RUTA + "/1/fotos/" + f.getId(), token).andExpect(status().isForbidden());
		}
	}

	@Test
	void unRolSinPermisoRecibe403AunqueLosParametrosSeanInvalidos() throws Exception {
		String token = jwt.generar(usuario(comunaA, Rol.ADMIN_COMUNA));

		pedir(RUTA + "?tamanio=0", token).andExpect(status().isForbidden());
	}

	@Test
	void soloLeen_noModificanNiBorran() throws Exception {
		reclamo(comunaA, vecinoA, 1);

		mockMvc.perform(post(RUTA).header("Authorization", "Bearer " + tokenVecinoA)).andExpect(status().isMethodNotAllowed());
		mockMvc.perform(delete(RUTA + "/1").header("Authorization", "Bearer " + tokenVecinoA))
				.andExpect(status().isMethodNotAllowed());
	}

	// ---- Lista: aislamiento, orden y paginación

	@Test
	void laListaSoloTraeLosReclamosDelVecinoEnSuComuna() throws Exception {
		reclamo(comunaA, vecinoA, 1);
		reclamo(comunaA, otroVecinoA, 2);
		// Mismo número, otra comuna y otro vecino.
		reclamo(comunaB, vecinoB, 1);

		pedir(RUTA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.reclamos", hasSize(1)))
				.andExpect(jsonPath("$.reclamos[0].numero").value(1))
				.andExpect(jsonPath("$.reclamos[0].descripcion").value("Pozo número 1"));
	}

	@Test
	void unVecinoSinReclamosRecibeUnaListaVacia() throws Exception {
		pedir(RUTA)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reclamos", hasSize(0)))
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(20));
	}

	@Test
	void laListaVieneDelMasNuevoAlMasViejo() throws Exception {
		Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
		reclamo(comunaA, vecinoA, 1, base);
		reclamo(comunaA, vecinoA, 2, base.plus(2, ChronoUnit.DAYS));
		reclamo(comunaA, vecinoA, 3, base.plus(1, ChronoUnit.DAYS));

		pedir(RUTA).andExpect(jsonPath("$.reclamos[*].numero", contains(2, 3, 1)));
	}

	@Test
	void laPaginacionCortaYCuentaBien() throws Exception {
		Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
		for (int i = 1; i <= 5; i++) {
			reclamo(comunaA, vecinoA, i, base.plus(i, ChronoUnit.HOURS));
		}

		pedir(RUTA + "?pagina=0&tamanio=2")
				.andExpect(jsonPath("$.reclamos[*].numero", contains(5, 4)))
				.andExpect(jsonPath("$.total").value(5))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(2));
		pedir(RUTA + "?pagina=2&tamanio=2").andExpect(jsonPath("$.reclamos[*].numero", contains(1)));
		pedir(RUTA + "?pagina=3&tamanio=2").andExpect(jsonPath("$.reclamos", hasSize(0)))
				.andExpect(jsonPath("$.total").value(5));
	}

	@Test
	void elTamanioPorDefectoEs20() throws Exception {
		Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
		for (int i = 1; i <= 21; i++) {
			reclamo(comunaA, vecinoA, i, base.plus(i, ChronoUnit.MINUTES));
		}

		pedir(RUTA).andExpect(jsonPath("$.reclamos", hasSize(20))).andExpect(jsonPath("$.total").value(21));
	}

	@Test
	void paginaOTamanioFueraDeRangoDan400Validacion() throws Exception {
		for (String consulta : List.of("?pagina=-1", "?pagina=1001", "?tamanio=0", "?tamanio=51", "?tamanio=-3")) {
			pedir(RUTA + consulta)
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
		pedir(RUTA + "?tamanio=51").andExpect(jsonPath("$.errores.tamanio").exists());
		// Los límites de adentro del rango sí se aceptan.
		pedir(RUTA + "?pagina=1000").andExpect(status().isOk());
		pedir(RUTA + "?pagina=0&tamanio=1").andExpect(status().isOk());
		pedir(RUTA + "?pagina=0&tamanio=50").andExpect(status().isOk());
		// Un valor que no es número es un pedido mal formado.
		pedir(RUTA + "?pagina=abc").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
	}

	@Test
	void laListaTraeMiniaturaYUltimoCambioDeEstado() throws Exception {
		Reclamo conFotos = reclamo(comunaA, vecinoA, 1);
		// La del trabajo (orden 1) no cuenta como miniatura; la del vecino con el orden más bajo, sí.
		foto(conFotos, OrigenFoto.TRABAJO, 1, "image/jpeg");
		ReclamoFoto segunda = foto(conFotos, OrigenFoto.VECINO, 2, "image/jpeg");
		ReclamoFoto primera = foto(conFotos, OrigenFoto.VECINO, 1, "image/png");
		Reclamo sinFotos = reclamo(comunaA, vecinoA, 2, Instant.now().minus(2, ChronoUnit.HOURS));
		assertThat(segunda.getId()).isNotEqualTo(primera.getId());
		cambiar(conFotos, CambioDeEstado.a(EstadoReclamo.ASIGNADO));

		pedir(RUTA)
				// El 2 se creó después que el 1, así que va primero.
				.andExpect(jsonPath("$.reclamos[0].numero").value(2))
				.andExpect(jsonPath("$.reclamos[0].idFotoPrincipal").value(nullValue()))
				.andExpect(jsonPath("$.reclamos[0].estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.reclamos[0].tipo").value("BACHE"))
				.andExpect(jsonPath("$.reclamos[0].tipoNombre").isNotEmpty())
				.andExpect(jsonPath("$.reclamos[0].direccion").value("Calle falsa 123"))
				.andExpect(jsonPath("$.reclamos[1].numero").value(1))
				.andExpect(jsonPath("$.reclamos[1].idFotoPrincipal").value(primera.getId().intValue()))
				.andExpect(jsonPath("$.reclamos[1].estado").value("ASIGNADO"));

		// El último cambio de estado es el de ASIGNADO (reciente); el del que nunca cambió es su propia creación.
		String cuerpo = pedir(RUTA).andReturn().getResponse().getContentAsString();
		Instant cambiadoUno = Instant.parse(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.reclamos[1].estadoCambiadoEn"));
		Instant creadoUno = Instant.parse(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.reclamos[1].creadoEn"));
		assertThat(cambiadoUno).isAfter(creadoUno);
		assertThat(Instant.parse(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.reclamos[0].estadoCambiadoEn")))
				.isAfterOrEqualTo(Instant.parse(com.jayway.jsonpath.JsonPath.read(cuerpo, "$.reclamos[0].creadoEn")));
		assertThat(sinFotos.getNumero()).isEqualTo(2);
	}

	// Sin N+1: la cantidad de consultas no depende de cuántos reclamos haya, y ninguna foto se carga en la sesión.
	@Test
	void laListaSeArmaConUnaCantidadFijaDeConsultasYSinCargarFotos() throws Exception {
		Statistics estadisticas = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
		estadisticas.setStatisticsEnabled(true);
		try {
			Instant base = Instant.now().minus(10, ChronoUnit.DAYS);
			for (int i = 1; i <= 6; i++) {
				Reclamo r = reclamo(comunaA, vecinoA, i, base.plus(i, ChronoUnit.HOURS));
				foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
				foto(r, OrigenFoto.VECINO, 2, "image/jpeg");
			}
			em.flush();
			em.clear();
			estadisticas.clear();

			pedir(RUTA + "?tamanio=6").andExpect(jsonPath("$.reclamos", hasSize(6)));

			// Una consulta para la página, una para el conteo y, a lo sumo, la del usuario del token.
			assertThat(estadisticas.getPrepareStatementCount()).as("consultas SQL").isLessThanOrEqualTo(3);
			assertThat(estadisticas.getEntityStatistics(ReclamoFoto.class.getName()).getLoadCount()).as("fotos cargadas").isZero();
			assertThat(estadisticas.getEntityStatistics(Reclamo.class.getName()).getLoadCount()).as("reclamos cargados como entidad")
					.isZero();
		} finally {
			estadisticas.setStatisticsEnabled(false);
		}
	}

	// ---- Detalle

	@Test
	void elDetalleDeUnReclamoRecibidoTrabajaConLoSuyo() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 7);
		foto(r, OrigenFoto.VECINO, 1, "image/jpeg");

		pedir(RUTA + "/7")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(7))
				.andExpect(jsonPath("$.tipo").value("BACHE"))
				.andExpect(jsonPath("$.estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.descripcion").value("Pozo número 7"))
				.andExpect(jsonPath("$.referencia").value("Frente al kiosco"))
				.andExpect(jsonPath("$.direccion").value("Calle falsa 123"))
				.andExpect(jsonPath("$.latitud").value(-31.420083))
				.andExpect(jsonPath("$.longitud").value(-64.188776))
				.andExpect(jsonPath("$.fotos", hasSize(1)))
				.andExpect(jsonPath("$.historial", hasSize(1)))
				.andExpect(jsonPath("$.historial[0].estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.descarte").value(nullValue()))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue()));
	}

	@Test
	void elDetalleNoExponeQuienCambioElEstadoNiLaNotaInterna() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		cambiar(r, new CambioDeEstado(EstadoReclamo.ASIGNADO, null, null, null, null, "nota interna secreta", agenteA.getId()));

		String cuerpo = pedir(RUTA + "/1").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(cuerpo).doesNotContain("nota interna secreta", "usuario", "idUsuario", "observacion\":\"nota")
				.doesNotContain(agenteA.getCorreo());
		pedir(RUTA + "/1").andExpect(jsonPath("$.historial[0].usuario").doesNotExist())
				.andExpect(jsonPath("$.historial[1].observacion").doesNotExist())
				.andExpect(jsonPath("$.historial[1].estado").value("ASIGNADO"));
	}

	@Test
	void elHistorialVieneDelMasViejoAlMasNuevo() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Listo"));

		String cuerpo = pedir(RUTA + "/1")
				.andExpect(jsonPath("$.historial[*].estado", contains("RECIBIDO", "ASIGNADO", "EN_REPARACION", "RESUELTO")))
				.andReturn().getResponse().getContentAsString();

		List<String> fechas = com.jayway.jsonpath.JsonPath.read(cuerpo, "$.historial[*].fecha");
		for (int i = 1; i < fechas.size(); i++) {
			assertThat(Instant.parse(fechas.get(i))).isAfterOrEqualTo(Instant.parse(fechas.get(i - 1)));
		}
	}

	@Test
	void lasFotosVienenPrimeroLasDelVecinoYDespuesLasDelTrabajoCadaGrupoEnSuOrden() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		foto(r, OrigenFoto.TRABAJO, 2, "image/jpeg");
		foto(r, OrigenFoto.VECINO, 2, "image/png");
		foto(r, OrigenFoto.TRABAJO, 1, "image/jpeg");
		foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		cambiar(r, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Listo"));

		pedir(RUTA + "/1")
				.andExpect(jsonPath("$.fotos", hasSize(4)))
				.andExpect(jsonPath("$.fotos[*].origen", contains("VECINO", "VECINO", "TRABAJO", "TRABAJO")))
				.andExpect(jsonPath("$.fotos[*].orden", contains(1, 2, 1, 2)))
				.andExpect(jsonPath("$.fotos[0].id").isNumber())
				// Solo los datos de cada foto, nunca la imagen.
				.andExpect(jsonPath("$.fotos[0].imagen").doesNotExist())
				.andExpect(jsonPath("$.fotos[0].imagenTipo").doesNotExist());
	}

	@Test
	void elDetalleDeUnDuplicadoDescartadoTraeElMotivoYSoloElNumeroYEstadoDelOriginal() throws Exception {
		Reclamo original = reclamo(comunaA, otroVecinoA, 1);
		original.setDescripcion("Descripcion privada del original");
		reclamos.saveAndFlush(original);
		cambiar(original, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		Reclamo duplicado = reclamo(comunaA, vecinoA, 2);
		cambiar(duplicado, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, "Ya lo estamos atendiendo", 1));
		// El original sigue su camino: el vecino ve su estado actual.
		cambiar(original, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		String cuerpo = pedir(RUTA + "/2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("DESCARTADO"))
				.andExpect(jsonPath("$.descarte.motivo").value("DUPLICADO"))
				.andExpect(jsonPath("$.descarte.observacion").value("Ya lo estamos atendiendo"))
				.andExpect(jsonPath("$.descarte.numeroOriginal").value(1))
				.andExpect(jsonPath("$.descarte.estadoOriginal").value("EN_REPARACION"))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue()))
				.andExpect(jsonPath("$.historial[*].estado", contains("RECIBIDO", "DESCARTADO")))
				.andReturn().getResponse().getContentAsString();

		// Del original no se filtra nada más (ni su descripción ni su vecino).
		assertThat(cuerpo).doesNotContain("Descripcion privada del original").doesNotContain(otroVecinoA.getCorreo());
		com.jayway.jsonpath.JsonPath.parse(cuerpo).read("$.descarte", java.util.Map.class)
				.keySet().forEach(clave -> assertThat(clave).isIn("motivo", "observacion", "numeroOriginal", "estadoOriginal"));
	}

	@Test
	void elDetalleDeUnDescartadoSinOriginalNoTraeNumeroNiEstadoOriginal() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		cambiar(r, CambioDeEstado.descartar(MotivoDescarte.NO_ES_DE_LA_COMUNA, "Es de la ruta", null));

		pedir(RUTA + "/1")
				.andExpect(jsonPath("$.descarte.motivo").value("NO_ES_DE_LA_COMUNA"))
				.andExpect(jsonPath("$.descarte.observacion").value("Es de la ruta"))
				.andExpect(jsonPath("$.descarte.numeroOriginal").value(nullValue()))
				.andExpect(jsonPath("$.descarte.estadoOriginal").value(nullValue()));
	}

	@Test
	void elDetalleDeUnResueltoTraeLoRealizadoYLasFotosDelTrabajo() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		cambiar(r, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Se tapó el pozo con asfalto"));
		foto(r, OrigenFoto.TRABAJO, 1, "image/png");

		pedir(RUTA + "/1")
				.andExpect(jsonPath("$.estado").value("RESUELTO"))
				.andExpect(jsonPath("$.trabajoRealizado").value("Se tapó el pozo con asfalto"))
				.andExpect(jsonPath("$.descarte").value(nullValue()))
				.andExpect(jsonPath("$.fotos[*].origen", contains("VECINO", "TRABAJO")))
				.andExpect(jsonPath("$.historial[*].estado", contains("RECIBIDO", "ASIGNADO", "EN_REPARACION", "RESUELTO")));
	}

	@Test
	void siSeReabreUnResueltoYaNoSeMuestraLoRealizado() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Se tapó el pozo"));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		pedir(RUTA + "/1")
				.andExpect(jsonPath("$.estado").value("EN_REPARACION"))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue()));
	}

	// ---- Detalle: un reclamo ajeno es igual a uno inexistente

	@Test
	void ajenoDeOtraComunaOInexistenteDan404IdenticoEnElDetalle() throws Exception {
		reclamo(comunaA, otroVecinoA, 1);
		reclamo(comunaB, vecinoB, 2);

		String ajeno = pedir(RUTA + "/1").andExpect(status().isNotFound()).andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
				.andReturn().getResponse().getContentAsString();
		String deOtraComuna = pedir(RUTA + "/2").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String inexistente = pedir(RUTA + "/999").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
		String invalido = pedir(RUTA + "/0").andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

		assertThat(sinRuta(deOtraComuna)).isEqualTo(sinRuta(ajeno));
		assertThat(sinRuta(inexistente)).isEqualTo(sinRuta(ajeno));
		assertThat(sinRuta(invalido)).isEqualTo(sinRuta(ajeno));
	}

	@Test
	void unNumeroQueNoEsEnteroEsUnPedidoMalFormado() throws Exception {
		pedir(RUTA + "/abc").andExpect(status().isBadRequest());
	}

	// ---- Foto

	@Test
	void laFotoSeSirveConElTipoGuardadoYLasCabecerasDeSeguridad() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		ReclamoFoto jpeg = foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		ReclamoFoto png = foto(r, OrigenFoto.TRABAJO, 1, "image/png");
		cambiar(r, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Listo"));

		pedir(RUTA + "/1/fotos/" + jpeg.getId())
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().doesNotExist("Content-Disposition"))
				.andExpect(content().bytes(jpeg.getImagen()));
		pedir(RUTA + "/1/fotos/" + png.getId())
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().doesNotExist("Content-Disposition"))
				.andExpect(content().bytes(png.getImagen()));
	}

	@Test
	void laFotoDeOtroVecinoDeOtraComunaOInexistenteDa404Identico() throws Exception {
		Reclamo mio = reclamo(comunaA, vecinoA, 1);
		foto(mio, OrigenFoto.VECINO, 1, "image/jpeg");
		Reclamo deOtroVecino = reclamo(comunaA, otroVecinoA, 2);
		ReclamoFoto fotoAjena = foto(deOtroVecino, OrigenFoto.VECINO, 1, "image/jpeg");
		Reclamo deOtraComuna = reclamo(comunaB, vecinoB, 1);
		ReclamoFoto fotoOtraComuna = foto(deOtraComuna, OrigenFoto.VECINO, 1, "image/jpeg");

		String esperado = pedir(RUTA + "/1/fotos/99999999").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();
		// La foto existe pero es de un reclamo de otro vecino (se pide con su número o con el mío).
		assertThat(sinRuta(pedir(RUTA + "/2/fotos/" + fotoAjena.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString())).isEqualTo(sinRuta(esperado));
		assertThat(sinRuta(pedir(RUTA + "/1/fotos/" + fotoAjena.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString())).isEqualTo(sinRuta(esperado));
		// La foto es de otra comuna, que además tiene un reclamo con el mismo número.
		assertThat(sinRuta(pedir(RUTA + "/1/fotos/" + fotoOtraComuna.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString())).isEqualTo(sinRuta(esperado));
		// Un reclamo inexistente.
		assertThat(sinRuta(pedir(RUTA + "/999/fotos/" + fotoAjena.getId()).andExpect(status().isNotFound()).andReturn().getResponse()
				.getContentAsString())).isEqualTo(sinRuta(esperado));
	}

	@Test
	void laFotoTieneQuePertenecerAlReclamoDelNumeroPedido() throws Exception {
		Reclamo uno = reclamo(comunaA, vecinoA, 1);
		Reclamo dos = reclamo(comunaA, vecinoA, 2);
		ReclamoFoto deDos = foto(dos, OrigenFoto.VECINO, 1, "image/jpeg");
		foto(uno, OrigenFoto.VECINO, 1, "image/jpeg");

		// Ambos reclamos son del vecino, pero la foto es del 2: pedida con el 1 no se entrega.
		pedir(RUTA + "/1/fotos/" + deDos.getId()).andExpect(status().isNotFound());
		pedir(RUTA + "/2/fotos/" + deDos.getId()).andExpect(status().isOk());
	}


	@Test
	void lasFotosDelTrabajoSoloSeMuestranSiElReclamoEstaResuelto() throws Exception {
		Reclamo r = reclamo(comunaA, vecinoA, 1);
		foto(r, OrigenFoto.VECINO, 1, "image/jpeg");
		ReclamoFoto deTrabajo = foto(r, OrigenFoto.TRABAJO, 1, "image/jpeg");
		cambiar(r, CambioDeEstado.a(EstadoReclamo.ASIGNADO));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		pedir(RUTA + "/1").andExpect(jsonPath("$.fotos[*].origen", contains("VECINO")));
		pedir(RUTA + "/1/fotos/" + deTrabajo.getId()).andExpect(status().isNotFound());

		cambiar(r, CambioDeEstado.resolver("Listo"));
		pedir(RUTA + "/1").andExpect(jsonPath("$.fotos[*].origen", contains("VECINO", "TRABAJO")));
		pedir(RUTA + "/1/fotos/" + deTrabajo.getId()).andExpect(status().isOk());

		// Al reabrirlo vuelven a ocultarse.
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		pedir(RUTA + "/1").andExpect(jsonPath("$.fotos[*].origen", contains("VECINO")));
		pedir(RUTA + "/1/fotos/" + deTrabajo.getId()).andExpect(status().isNotFound());
	}
}
