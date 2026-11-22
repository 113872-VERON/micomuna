package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.micomuna.services.LimiteDeComuna;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

import jakarta.persistence.EntityManager;

// MC-16: el punto del reclamo tiene que estar dentro del límite de la comuna (si tiene uno cargado).
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UbicacionReclamoTests {

	private static final String RUTA = "/api/reclamos";

	// El cuadrado del límite: longitud entre -64.20 y -64.18, latitud entre -31.43 y -31.41.
	private static final String CUADRADO = LimiteComunaTests.POLIGONO;
	// Otro cuadrado, más al este (longitud entre -64.10 y -64.08).
	private static final String OTRO_CUADRADO = "{\"type\":\"Polygon\",\"coordinates\":[" + LimiteComunaTests.OTRO_ANILLO + "]}";

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
	@Autowired
	private LimiteDeComuna limiteDeComuna;

	private Comuna comuna;
	private String tokenVecino;
	private String tokenSuperadmin;

	@BeforeEach
	void preparar() {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Comuna c = new Comuna();
		c.setNombre("Salsipuedes");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t16-alta");
		comuna = comunas.saveAndFlush(c);
		tokenVecino = jwt.generar(usuario(comuna, "vecino-t16@mail.com", Rol.VECINO));
		tokenSuperadmin = jwt.generar(usuario(null, "root-t16@mail.com", Rol.SUPERADMIN));
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

	private void cargarLimite(String figura) throws Exception {
		mockMvc.perform(put("/api/admin/comunas/" + comuna.getId() + "/limite")
				.header("Authorization", "Bearer " + tokenSuperadmin)
				.contentType(MediaType.APPLICATION_JSON).content(figura))
				.andExpect(status().isOk());
	}

	private static Map<String, String> en(String latitud, String longitud) {
		Map<String, String> campos = new LinkedHashMap<>();
		campos.put("tipo", "BACHE");
		campos.put("descripcion", "Hay un pozo enorme en la esquina");
		if (latitud != null) {
			campos.put("latitud", latitud);
		}
		if (longitud != null) {
			campos.put("longitud", longitud);
		}
		return campos;
	}

	private ResultActions reportar(Map<String, String> campos, MockMultipartFile... fotos) throws Exception {
		MockMultipartHttpServletRequestBuilder pedido = multipart(RUTA);
		campos.forEach(pedido::param);
		for (MockMultipartFile f : fotos) {
			pedido.file(f);
		}
		return mockMvc.perform(pedido.header("Authorization", "Bearer " + tokenVecino));
	}

	private JsonNode reportarBien(Map<String, String> campos) throws Exception {
		return json.readTree(reportar(campos).andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString());
	}

	private void esperarFueraDeComuna(String latitud, String longitud) throws Exception {
		reportar(en(latitud, longitud))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_COMUNA"))
				.andExpect(jsonPath("$.detail")
						.value("Ese lugar está fuera de Salsipuedes. Solo podés reportar problemas de tu comuna."));
	}

	private List<Reclamo> reclamos() {
		em.flush();
		em.clear();
		return em.createQuery("select r from Reclamo r where r.comuna.id = :id", Reclamo.class)
				.setParameter("id", comuna.getId()).getResultList();
	}

	private int contador() {
		em.flush();
		em.clear();
		return ((Number) em.createNativeQuery("select ultimo_numero_reclamo from comuna where id = :id")
				.setParameter("id", comuna.getId()).getSingleResult()).intValue();
	}

	// ---- Con límite

	@Test
	void unPuntoAdentroDelLimiteSeAcepta() throws Exception {
		cargarLimite(CUADRADO);
		JsonNode creado = reportarBien(en("-31.42", "-64.19"));
		assertThat(creado.get("numero").asInt()).isEqualTo(1);
		assertThat(reclamos()).hasSize(1);
	}

	@Test
	void unPuntoSobreElBordeCuentaComoAdentro() throws Exception {
		cargarLimite(CUADRADO);
		// Sobre un lado, sobre otro lado y sobre una esquina.
		reportarBien(en("-31.43", "-64.19"));
		reportarBien(en("-31.42", "-64.18"));
		reportarBien(en("-31.41", "-64.20"));
		assertThat(reclamos()).hasSize(3);
	}

	@Test
	void unPuntoAfueraDa400FueraDeComunaConElNombreDeLaComuna() throws Exception {
		cargarLimite(CUADRADO);
		esperarFueraDeComuna("-31.50", "-64.19");
		esperarFueraDeComuna("-31.42", "-64.00");
		// Apenas pasado del borde (el borde es -64.18).
		esperarFueraDeComuna("-31.42", "-64.179999");
		assertThat(reclamos()).isEmpty();
	}

	@Test
	void latitudYLongitudSeLeenEnElOrdenCorrecto() throws Exception {
		cargarLimite(CUADRADO);
		// Los mismos números con los dos campos cambiados quedan lejos de la comuna.
		esperarFueraDeComuna("-64.19", "-31.42");
	}

	@Test
	void laComprobacionDeZonaVieneAntesDeLasFotosYNoGastaElNumero() throws Exception {
		cargarLimite(CUADRADO);
		MockMultipartFile fotoRota = new MockMultipartFile("fotos", "foto.jpg", "image/jpeg", ImagenesDePrueba.gif(8, 8));
		// Con una foto inválida y un punto afuera, el error es el de la zona: las fotos ni se miraron.
		reportar(en("-31.50", "-64.19"), fotoRota)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FUERA_DE_COMUNA"));
		assertThat(contador()).isZero();
		assertThat(reclamos()).isEmpty();
	}

	@Test
	void unLimiteConAgujeroRechazaLosPuntosDelAgujero() throws Exception {
		cargarLimite("{\"type\":\"Polygon\",\"coordinates\":[" + "[[-64.20,-31.43],[-64.18,-31.43],[-64.18,-31.41],[-64.20,-31.41],[-64.20,-31.43]],"
				+ "[[-64.195,-31.425],[-64.19,-31.425],[-64.19,-31.42],[-64.195,-31.42],[-64.195,-31.425]]]}");
		esperarFueraDeComuna("-31.4225", "-64.1925");
		reportarBien(en("-31.415", "-64.185"));
	}

	@Test
	void funcionaConUnMultiPolygon() throws Exception {
		cargarLimite("{\"type\":\"MultiPolygon\",\"coordinates\":[[" + "[[-64.20,-31.43],[-64.18,-31.43],[-64.18,-31.41],[-64.20,-31.41],[-64.20,-31.43]]"
				+ "],[" + LimiteComunaTests.OTRO_ANILLO + "]]}");
		reportarBien(en("-31.42", "-64.19"));
		reportarBien(en("-31.42", "-64.09"));
		// Entre las dos figuras.
		esperarFueraDeComuna("-31.42", "-64.14");
	}


	// ---- Límite guardado dañado

	@Test
	void unLimiteDanadoNoBloqueaNoSeRecuerdaYAlArreglarloVuelveAControlar(CapturedOutput salida) throws Exception {
		String danado = "esto-no-es-un-geojson-DANADO";
		guardarTextoDelLimite(danado);
		LimiteDeComuna limite = limiteDeComuna;

		// Con el dato dañado no se bloquea a nadie, aunque el punto esté lejos.
		reportarBien(en("-31.50", "-64.19"));
		reportarBien(en("-34.60", "-58.38"));
		// No se recuerda como "sin límite": se reintenta en cada alta.
		@SuppressWarnings("unchecked")
		Map<Long, ?> cache = (Map<Long, ?>) ReflectionTestUtils.getField(limite, "cache");
		assertThat(cache).doesNotContainKey(comuna.getId());
		// Se registra con nivel ERROR y sin el contenido del límite.
		assertThat(salida.getAll()).contains("ERROR").contains("No se pudo leer el límite guardado de la comuna")
				.doesNotContain(danado);

		// Al arreglar el dato, vuelve a controlar sin reiniciar nada.
		guardarTextoDelLimite(CUADRADO);
		esperarFueraDeComuna("-31.50", "-64.19");
		reportarBien(en("-31.42", "-64.19"));
		assertThat(cache).containsKey(comuna.getId());
	}

	private void guardarTextoDelLimite(String texto) {
		em.flush();
		em.createNativeQuery("update comuna set limite = :t, limite_cargado_en = current_timestamp where id = :id")
				.setParameter("t", texto).setParameter("id", comuna.getId()).executeUpdate();
		em.clear();
	}
	// ---- Sin límite y caché

	@Test
	void unaComunaSinLimiteAceptaCualquierPunto() throws Exception {
		reportarBien(en("-31.42", "-64.19"));
		reportarBien(en("-34.60", "-58.38"));
		reportarBien(en("0", "0"));
		assertThat(reclamos()).hasSize(3);
	}

	@Test
	void despuesDeCargarUnLimiteNuevoElServidorUsaElNuevo() throws Exception {
		// Primero sin límite (el servidor recuerda "sin límite").
		reportarBien(en("-31.42", "-64.09"));
		// Con el primer límite, ese punto queda afuera.
		cargarLimite(CUADRADO);
		esperarFueraDeComuna("-31.42", "-64.09");
		reportarBien(en("-31.42", "-64.19"));
		// Con un límite nuevo, el punto de antes entra y el de ahora queda afuera.
		cargarLimite(OTRO_CUADRADO);
		reportarBien(en("-31.42", "-64.09"));
		esperarFueraDeComuna("-31.42", "-64.19");
	}

	@Test
	void ElLimiteDeUnaComunaNoAfectaAOtra() throws Exception {
		cargarLimite(CUADRADO);
		Comuna c = new Comuna();
		c.setNombre("Otra");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t16-alta-otra");
		Comuna otra = comunas.saveAndFlush(c);
		String tokenDeLaOtra = jwt.generar(usuario(otra, "vecino-otra-t16@mail.com", Rol.VECINO));

		mockMvc.perform(multipart(RUTA).param("tipo", "BACHE").param("descripcion", "Pozo")
				.param("latitud", "-31.50").param("longitud", "-64.19")
				.header("Authorization", "Bearer " + tokenDeLaOtra))
				.andExpect(status().isCreated());
	}

	// ---- Obligatoriedad, referencia y dirección

	@Test
	void sinLatitudNiLongitudDa400UbicacionRequeridaConUnMensajeParaElVecino() throws Exception {
		reportar(en(null, null))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("UBICACION_REQUERIDA"))
				.andExpect(jsonPath("$.detail").value("Marcá en el mapa dónde está el problema o tocá «Usar mi ubicación»."));
		assertThat(reclamos()).isEmpty();
	}

	@Test
	void unaReferenciaSolaNoReemplazaLasCoordenadas() throws Exception {
		Map<String, String> campos = en(null, null);
		campos.put("referencia", "Frente a la panadería");
		reportar(campos).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("UBICACION_REQUERIDA"));
	}

	@Test
	void conSoloUnaDeLasDosCoordenadasDa400() throws Exception {
		reportar(en("-31.42", null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.longitud").exists());
		reportar(en(null, "-64.19")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.latitud").exists());
	}

	@Test
	void laReferenciaEsOpcional() throws Exception {
		Reclamo guardado = reclamo(reportarBien(en("-31.42", "-64.19")));
		assertThat(guardado.getReferencia()).isNull();
		assertThat(guardado.getDireccion()).isNull();
	}

	@Test
	void laDireccionSeGuardaSinEspaciosDeMas() throws Exception {
		Map<String, String> campos = en("-31.42", "-64.19");
		campos.put("direccion", "  Av. Colón 1234, Córdoba  ");
		assertThat(reclamo(reportarBien(campos)).getDireccion()).isEqualTo("Av. Colón 1234, Córdoba");
	}

	@Test
	void unaDireccionEnBlancoEsComoNoMandarla() throws Exception {
		Map<String, String> campos = en("-31.42", "-64.19");
		campos.put("direccion", "    ");
		assertThat(reclamo(reportarBien(campos)).getDireccion()).isNull();
	}

	@Test
	void laDireccionPuedeTener300CaracteresPeroNo301() throws Exception {
		Map<String, String> campos = en("-31.42", "-64.19");
		campos.put("direccion", "d".repeat(300));
		assertThat(reclamo(reportarBien(campos)).getDireccion()).hasSize(300);

		campos.put("direccion", "d".repeat(301));
		reportar(campos)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.direccion").exists());
		assertThat(reclamos()).hasSize(1);
	}

	@Test
	void laDireccionNoSirveParaValidarLaZona() throws Exception {
		cargarLimite(CUADRADO);
		Map<String, String> campos = en("-31.50", "-64.19");
		campos.put("direccion", "Dentro de la comuna, Salsipuedes");
		reportar(campos).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FUERA_DE_COMUNA"));
	}

	private Reclamo reclamo(JsonNode respuesta) {
		em.flush();
		em.clear();
		return em.find(Reclamo.class, respuesta.get("id").asLong());
	}
}
