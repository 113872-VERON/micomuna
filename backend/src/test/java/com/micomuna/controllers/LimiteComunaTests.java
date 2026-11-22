package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import org.hibernate.engine.spi.SessionImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.ComunaLimite;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

import jakarta.persistence.EntityManager;

// MC-16: el superadmin carga el límite geográfico de una comuna (PUT /api/admin/comunas/{id}/limite) y los usuarios
// de la comuna lo leen (GET /api/mi-comuna/limite).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LimiteComunaTests {

	private static final String RUTA = "/api/admin/comunas";
	private static final String RUTA_MI_COMUNA = "/api/mi-comuna/limite";

	// Un cuadrado de unos 2 km de lado sobre Córdoba capital. Posiciones [longitud, latitud], como manda GeoJSON.
	static final String ANILLO = "[[-64.20,-31.43],[-64.18,-31.43],[-64.18,-31.41],[-64.20,-31.41],[-64.20,-31.43]]";
	static final String POLIGONO = "{\"type\":\"Polygon\",\"coordinates\":[" + ANILLO + "]}";
	static final String OTRO_ANILLO = "[[-64.10,-31.43],[-64.08,-31.43],[-64.08,-31.41],[-64.10,-31.41],[-64.10,-31.43]]";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ComunaRepository comunas;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private JwtService jwt;
	@Autowired
	private ObjectMapper json;
	@Autowired
	private EntityManager em;

	private Comuna comuna;
	private String tokenSuperadmin;

	@BeforeEach
	void preparar() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comuna = comuna("t16-limite");
		tokenSuperadmin = jwt.generar(usuario(null, "root16@mail.com", Rol.SUPERADMIN));
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna deLaComuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(deLaComuna);
		u.setCorreo(correo);
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private ResultActions cargar(long id, String token, String cuerpo) throws Exception {
		var pedido = put(RUTA + "/" + id + "/limite").contentType(MediaType.APPLICATION_JSON).content(cuerpo);
		return mockMvc.perform(token == null ? pedido : pedido.header("Authorization", "Bearer " + token));
	}

	private ResultActions cargar(String cuerpo) throws Exception {
		return cargar(comuna.getId(), tokenSuperadmin, cuerpo);
	}

	private JsonNode leerComoVecino(String token) throws Exception {
		String cuerpo = mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return json.readTree(cuerpo);
	}

	private String tokenVecino() {
		return jwt.generar(usuario(comuna, "vecino16@mail.com", Rol.VECINO));
	}

	private boolean tieneLimiteEnLaBase() {
		em.flush();
		em.clear();
		return comunas.findById(comuna.getId()).orElseThrow().tieneLimite();
	}

	// ---- Carga correcta

	@Test
	void cargaUnPolygonYDevuelveLaComuna() throws Exception {
		cargar(POLIGONO)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(comuna.getId()))
				.andExpect(jsonPath("$.tieneLimite").value(true))
				.andExpect(jsonPath("$.limiteCargadoEn").isNotEmpty());
		assertThat(tieneLimiteEnLaBase()).isTrue();

		JsonNode figura = leerComoVecino(tokenVecino());
		assertThat(figura.get("type").asText()).isEqualTo("Polygon");
		assertThat(figura.get("coordinates").get(0)).hasSize(5);
		assertThat(figura.get("coordinates").get(0).get(0).get(0).asDouble()).isEqualTo(-64.20);
		assertThat(figura.get("coordinates").get(0).get(0).get(1).asDouble()).isEqualTo(-31.43);
	}

	@Test
	void cargaUnMultiPolygon() throws Exception {
		cargar("{\"type\":\"MultiPolygon\",\"coordinates\":[[" + ANILLO + "],[" + OTRO_ANILLO + "]]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneLimite").value(true));
		JsonNode figura = leerComoVecino(tokenVecino());
		assertThat(figura.get("type").asText()).isEqualTo("MultiPolygon");
		assertThat(figura.get("coordinates")).hasSize(2);
	}

	@Test
	void cargaUnFeatureYGuardaSoloLaFiguraSinPropiedades() throws Exception {
		cargar("{\"type\":\"Feature\",\"properties\":{\"nombre\":\"dato-del-archivo\"},\"geometry\":" + POLIGONO + "}")
				.andExpect(status().isOk());
		String guardado = mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + tokenVecino()))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		assertThat(guardado).doesNotContain("dato-del-archivo").doesNotContain("properties").doesNotContain("Feature");
		assertThat(json.readTree(guardado).get("type").asText()).isEqualTo("Polygon");
	}

	@Test
	void cargaUnFeatureCollectionConUnaSolaFigura() throws Exception {
		cargar("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":"
				+ POLIGONO + "}]}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneLimite").value(true));
		assertThat(leerComoVecino(tokenVecino()).get("type").asText()).isEqualTo("Polygon");
	}

	@Test
	void unPoligonoConAgujeroEsValidoYIgnoraLaAltura() throws Exception {
		String conAgujero = "{\"type\":\"Polygon\",\"coordinates\":[[[-64.20,-31.43,500],[-64.18,-31.43,500],"
				+ "[-64.18,-31.41,500],[-64.20,-31.41,500],[-64.20,-31.43,500]],"
				+ "[[-64.195,-31.425],[-64.19,-31.425],[-64.19,-31.42],[-64.195,-31.42],[-64.195,-31.425]]]}";
		cargar(conAgujero).andExpect(status().isOk());
		JsonNode figura = leerComoVecino(tokenVecino());
		assertThat(figura.get("coordinates")).hasSize(2);
		assertThat(figura.get("coordinates").get(0).get(0)).hasSize(2);
	}

	@Test
	void unLimiteNuevoReemplazaAlAnterior() throws Exception {
		cargar(POLIGONO).andExpect(status().isOk());
		cargar("{\"type\":\"Polygon\",\"coordinates\":[" + OTRO_ANILLO + "]}").andExpect(status().isOk());
		assertThat(leerComoVecino(tokenVecino()).get("coordinates").get(0).get(0).get(0).asDouble()).isEqualTo(-64.10);
	}

	// ---- Rechazos

	static Stream<Arguments> limitesInvalidos() {
		String puntoFuera = "[-64.5,-31.5]";
		return Stream.of(
				Arguments.of("no es JSON", "esto no es un geojson"),
				Arguments.of("JSON cortado", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.2,-31.4]"),
				Arguments.of("texto extra después del JSON", POLIGONO + " basura"),
				Arguments.of("la raíz es una lista", "[]"),
				Arguments.of("sin type", "{\"coordinates\":[" + ANILLO + "]}"),
				Arguments.of("Point", "{\"type\":\"Point\",\"coordinates\":[-64.19,-31.42]}"),
				Arguments.of("LineString", "{\"type\":\"LineString\",\"coordinates\":[[-64.2,-31.4],[-64.1,-31.3]]}"),
				Arguments.of("GeometryCollection", "{\"type\":\"GeometryCollection\",\"geometries\":[" + POLIGONO + "]}"),
				Arguments.of("Feature con un Point", "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[-64.19,-31.42]}}"),
				Arguments.of("Feature sin geometry", "{\"type\":\"Feature\",\"properties\":{}}"),
				Arguments.of("Feature con geometry nula", "{\"type\":\"Feature\",\"geometry\":null}"),
				Arguments.of("FeatureCollection con dos figuras", "{\"type\":\"FeatureCollection\",\"features\":["
						+ "{\"type\":\"Feature\",\"geometry\":" + POLIGONO + "},{\"type\":\"Feature\",\"geometry\":" + POLIGONO + "}]}"),
				Arguments.of("FeatureCollection vacía", "{\"type\":\"FeatureCollection\",\"features\":[]}"),
				Arguments.of("anillo abierto", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.20,-31.43],[-64.18,-31.43],[-64.18,-31.41],[-64.20,-31.41],[-64.19,-31.40]]]}"),
				Arguments.of("anillo con menos de 4 puntos", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.20,-31.43],[-64.18,-31.43],[-64.20,-31.43]]]}"),
				Arguments.of("polígono sin anillos", "{\"type\":\"Polygon\",\"coordinates\":[]}"),
				Arguments.of("MultiPolygon sin polígonos", "{\"type\":\"MultiPolygon\",\"coordinates\":[]}"),
				Arguments.of("longitud fuera de rango", "{\"type\":\"Polygon\",\"coordinates\":[[[200,-31.43],[-64.18,-31.43],[-64.18,-31.41],[200,-31.43]]]}"),
				Arguments.of("latitud fuera de rango", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.20,91],[-64.18,-31.43],[-64.18,-31.41],[-64.20,91]]]}"),
				// Una figura de Sídney (lon 151, lat -33) con la latitud primero: la "longitud" -33 sirve, pero la "latitud" 151 no.
				Arguments.of("latitud y longitud invertidas", "{\"type\":\"Polygon\",\"coordinates\":[[[-33.9,151.2],[-33.9,151.3],[-33.8,151.3],[-33.8,151.2],[-33.9,151.2]]]}"),
				Arguments.of("coordenada que no es un número", "{\"type\":\"Polygon\",\"coordinates\":[[[\"a\",-31.43],[-64.18,-31.43],[-64.18,-31.41],[\"a\",-31.43]]]}"),
				Arguments.of("punto con una sola coordenada", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.20],[-64.18,-31.43],[-64.18,-31.41],[-64.20]]]}"),
				Arguments.of("número gigante", "{\"type\":\"Polygon\",\"coordinates\":[[[1e999,-31.43],[-64.18,-31.43],[-64.18,-31.41],[1e999,-31.43]]]}"),
				Arguments.of("polígono que se corta a sí mismo (moño)", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.20,-31.43],[-64.18,-31.41],[-64.18,-31.43],[-64.20,-31.41],[-64.20,-31.43]]]}"),
				Arguments.of("agujero fuera del polígono", "{\"type\":\"Polygon\",\"coordinates\":[" + ANILLO
						+ ",[" + puntoFuera + ",[-64.4,-31.5],[-64.4,-31.4],[-64.5,-31.4],[-64.5,-31.5]]]}"),
				Arguments.of("polígono sin superficie", "{\"type\":\"Polygon\",\"coordinates\":[[[-64.2,-31.4],[-64.1,-31.4],[-64.3,-31.4],[-64.2,-31.4]]]}"));
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("limitesInvalidos")
	void rechazaUnLimiteInvalido(String caso, String cuerpo) throws Exception {
		cargar(cuerpo)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));
		assertThat(tieneLimiteEnLaBase()).as("no se guardó nada").isFalse();
	}


	// ---- Tope de puntos y de profundidad

	@Test
	void masDe100milPuntosEnTotalDa400() throws Exception {
		// Un solo anillo de 100.001 puntos (el chequeo corta antes de validar la figura).
		StringBuilder anillo = new StringBuilder("[");
		for (int i = 0; i < 100_001; i++) {
			anillo.append(i == 0 ? "" : ",").append("[1,1]");
		}
		anillo.append("]");
		cargar("{\"type\":\"Polygon\",\"coordinates\":[" + anillo + "]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"))
				.andExpect(jsonPath("$.detail").value("El límite tiene demasiados puntos. Simplificalo y volvé a subirlo."));
		assertThat(tieneLimiteEnLaBase()).isFalse();
	}

	@Test
	void elTopeDePuntosSumaTodosLosPoligonosDeUnMultiPolygon() throws Exception {
		StringBuilder anillo = new StringBuilder("[");
		for (int i = 0; i < 60_000; i++) {
			anillo.append(i == 0 ? "" : ",").append("[1,1]");
		}
		anillo.append("]");
		// Dos polígonos de 60.000 puntos: cada uno entra solo, juntos pasan los 100.000.
		cargar("{\"type\":\"MultiPolygon\",\"coordinates\":[[" + anillo + "],[" + anillo + "]]}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("demasiados puntos")));
	}

	@Test
	void unJsonMuyAnidadoSeRechazaComoLimiteInvalido() throws Exception {
		String anidado = "[".repeat(25) + "]".repeat(25);
		cargar("{\"type\":\"Polygon\",\"coordinates\":" + anidado + "}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"));
		// Con 20 niveles de profundidad máxima, un FeatureCollection normal (7 niveles) sigue entrando.
		cargar("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},\"geometry\":" + POLIGONO + "}]}")
				.andExpect(status().isOk());
	}
	@Test
	void unLimiteInvalidoNoPisaElAnterior() throws Exception {
		cargar(POLIGONO).andExpect(status().isOk());
		cargar("{\"type\":\"Point\",\"coordinates\":[-64.19,-31.42]}").andExpect(status().isBadRequest());
		assertThat(leerComoVecino(tokenVecino()).get("type").asText()).isEqualTo("Polygon");
	}

	@Test
	void losMensajesEstanEnCastellanoYDicenQueHacer() throws Exception {
		cargar("{\"type\":\"Point\",\"coordinates\":[-64.19,-31.42]}")
				.andExpect(jsonPath("$.detail").value("El archivo trae un Point y hace falta un polígono (Polygon o MultiPolygon)."));
		cargar("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"geometry\":" + POLIGONO
				+ "},{\"type\":\"Feature\",\"geometry\":" + POLIGONO + "}]}")
				.andExpect(jsonPath("$.detail").value(containsString("más de una figura")));
		cargar("{\"type\":\"Polygon\",\"coordinates\":[[[200,-31.43],[-64.18,-31.43],[-64.18,-31.41],[200,-31.43]]]}")
				.andExpect(jsonPath("$.detail").value(containsString("primero la longitud")));
		cargar("{\"type\":\"Polygon\",\"coordinates\":[[[-64.20,-31.43],[-64.18,-31.41],[-64.18,-31.43],[-64.20,-31.41],[-64.20,-31.43]]]}")
				.andExpect(jsonPath("$.detail").value(containsString("se cruza")));
	}

	@Test
	void masDeUnMegaDa400SinImportarElContenido() throws Exception {
		// Un GeoJSON correcto, pero con relleno hasta pasar 1 MB.
		String grande = POLIGONO + " ".repeat(1024 * 1024);
		cargar(grande)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LIMITE_INVALIDO"))
				.andExpect(jsonPath("$.detail").value(containsString("1 MB")));
		assertThat(tieneLimiteEnLaBase()).isFalse();
	}

	@Test
	void unArchivoDeExactamenteUnMegaEsValido() throws Exception {
		String justo = POLIGONO + " ".repeat(1024 * 1024 - POLIGONO.length());
		cargar(justo).andExpect(status().isOk());
	}

	// ---- Acceso

	@Test
	void sinSesionDa401() throws Exception {
		cargar(comuna.getId(), null, POLIGONO).andExpect(status().isUnauthorized());
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "VECINO", "AGENTE", "ADMINISTRATIVO", "ADMIN_COMUNA" })
	void losRolesDeComunaNoPuedenCargarElLimite(Rol rol) throws Exception {
		String token = jwt.generar(usuario(comuna, "rol16-" + rol.name().toLowerCase() + "@mail.com", rol));
		cargar(comuna.getId(), token, POLIGONO)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		// Tampoco con un cuerpo inválido: el 403 llega antes que cualquier lectura del archivo.
		cargar(comuna.getId(), token, "basura").andExpect(status().isForbidden());
		assertThat(tieneLimiteEnLaBase()).isFalse();
	}

	@Test
	void unaComunaQueNoExisteDa404() throws Exception {
		cargar(Long.MAX_VALUE - 1, tokenSuperadmin, POLIGONO)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
	}

	// ---- La lista del superadmin

	@Test
	void laListaDicePorComunaSiTieneLimiteYCuandoSeCargo() throws Exception {
		Comuna sinLimite = comuna("t16-sin-limite");
		cargar(POLIGONO).andExpect(status().isOk());

		String cuerpo = mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode con = null;
		JsonNode sin = null;
		for (JsonNode c : json.readTree(cuerpo)) {
			if (c.get("id").asLong() == comuna.getId()) {
				con = c;
			} else if (c.get("id").asLong() == sinLimite.getId()) {
				sin = c;
			}
		}
		assertThat(con).isNotNull();
		assertThat(con.get("tieneLimite").asBoolean()).isTrue();
		assertThat(con.get("limiteCargadoEn").asText()).isNotBlank();
		assertThat(sin).isNotNull();
		assertThat(sin.get("tieneLimite").asBoolean()).isFalse();
		assertThat(sin.get("limiteCargadoEn").isNull()).isTrue();
		// La figura nunca va en la lista.
		assertThat(cuerpo).doesNotContain("coordinates");
	}

	@Test
	void listarYLeerUnaComunaNoTraenElCLOBDelLimite() throws Exception {
		cargar(POLIGONO).andExpect(status().isOk());
		em.flush();
		em.clear();

		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenSuperadmin)).andExpect(status().isOk());
		comunas.findById(comuna.getId()).orElseThrow();

		assertThat(entidadesCargadas()).doesNotContain(ComunaLimite.class);
		// Control: leer el límite con su propia entidad sí lo trae.
		mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + tokenVecino())).andExpect(status().isOk());
		assertThat(entidadesCargadas()).contains(ComunaLimite.class);
	}

	private java.util.List<Class<?>> entidadesCargadas() {
		return em.unwrap(SessionImplementor.class).getPersistenceContext().getEntitiesByKey().values().stream()
				.<Class<?>>map(Object::getClass).distinct().toList();
	}

	// ---- GET /api/mi-comuna/limite

	@Test
	void unaComunaSinLimiteResponde204() throws Exception {
		mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + tokenVecino()))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));
	}

	@Test
	void miComunaDevuelveElJsonDeLaFiguraConSuTipoDeContenido() throws Exception {
		cargar(POLIGONO).andExpect(status().isOk());
		mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + tokenVecino()))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.type").value("Polygon"))
				.andExpect(jsonPath("$.coordinates[0]").isArray());
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "VECINO", "AGENTE", "ADMINISTRATIVO", "ADMIN_COMUNA" })
	void todosLosRolesDeComunaLeenElLimite(Rol rol) throws Exception {
		cargar(POLIGONO).andExpect(status().isOk());
		String token = jwt.generar(usuario(comuna, "lee16-" + rol.name().toLowerCase() + "@mail.com", rol));
		mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("Polygon"));
	}

	@Test
	void laComunaSaleDelTokenYNoDeLoQueMandaElCliente() throws Exception {
		Comuna otra = comuna("t16-otra");
		cargar(POLIGONO).andExpect(status().isOk());
		String tokenDeLaOtra = jwt.generar(usuario(otra, "vecino-otra16@mail.com", Rol.VECINO));

		// El vecino de la otra comuna no ve el límite de esta, ni con parámetros que intenten cambiarla.
		mockMvc.perform(get(RUTA_MI_COMUNA).param("id", String.valueOf(comuna.getId()))
				.param("comunaId", String.valueOf(comuna.getId()))
				.header("X-Comuna", String.valueOf(comuna.getId()))
				.header("Authorization", "Bearer " + tokenDeLaOtra))
				.andExpect(status().isNoContent());
		// Y el de esta comuna sigue viendo el suyo.
		mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + tokenVecino()))
				.andExpect(status().isOk())
				.andExpect(content().string(not("")));
	}

	@Test
	void elSuperadminNoPuedeLeerElLimiteDeMiComuna() throws Exception {
		mockMvc.perform(get(RUTA_MI_COMUNA).header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
	}

	@Test
	void miComunaSinSesionDa401() throws Exception {
		mockMvc.perform(get(RUTA_MI_COMUNA)).andExpect(status().isUnauthorized());
	}
}
