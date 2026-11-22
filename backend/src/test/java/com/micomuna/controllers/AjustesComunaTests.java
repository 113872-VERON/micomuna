package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.micomuna.client.MailService;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

import jakarta.persistence.EntityManager;

// MC-76: teléfono y WhatsApp de la comuna, y la fecha de última modificación (actualizadaEn).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AjustesComunaTests {

	private static final String RUTA = "/api/admin/comunas";
	private static final Instant FECHA_VIEJA = Instant.parse("2020-01-01T00:00:00Z");

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

	@MockitoBean
	private MailService mail;

	private String tokenSuperadmin;

	@BeforeEach
	void preparar() {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario root = new Usuario();
		root.setCorreo("root76@mail.com");
		root.setClaveHash("hash");
		root.setNombre("Root");
		root.setRol(Rol.SUPERADMIN);
		root.setActivo(true);
		tokenSuperadmin = jwt.generar(usuarios.saveAndFlush(root));
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	// Deja la fecha de última modificación en 2020, para que cualquier cambio posterior se note.
	private void fijarFechaVieja(long id) {
		em.flush();
		em.createNativeQuery("update comuna set actualizada_en = TIMESTAMP '2020-01-01 00:00:00' where id = :id")
				.setParameter("id", id).executeUpdate();
		em.clear();
	}

	private ResultActions crear(String extra) throws Exception {
		String cuerpo = "{\"nombre\":\"Comuna Setenta\",\"provincia\":\"CORDOBA\",\"adminCorreo\":\"jefe76@mail.com\","
				+ "\"adminNombre\":\"Jefe\"" + extra + "}";
		return mockMvc.perform(post(RUTA).header("Authorization", "Bearer " + tokenSuperadmin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private ResultActions modificar(long id, String cuerpo) throws Exception {
		return mockMvc.perform(patch(RUTA + "/" + id).header("Authorization", "Bearer " + tokenSuperadmin)
				.contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

	private Instant actualizadaEn(ResultActions respuesta) throws Exception {
		JsonNode nodo = json.readTree(respuesta.andReturn().getResponse().getContentAsString());
		return Instant.parse(nodo.get("actualizadaEn").asText());
	}

	// ---- Alta

	@Test
	void altaConTelefonoYWhatsappLosNormalizaYDevuelveLasFechas() throws Exception {
		crear(",\"telefono\":\"+54 9 3543 12-3456\",\"whatsapp\":\"(54) 9 351 555-1234\"")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.telefono").value("5493543123456"))
				.andExpect(jsonPath("$.comuna.whatsapp").value("5493515551234"))
				.andExpect(jsonPath("$.comuna.creadaEn").exists())
				.andExpect(jsonPath("$.comuna.actualizadaEn").exists());
	}

	@Test
	void altaConTelefonoEnBlancoLoDejaVacio() throws Exception {
		crear(",\"telefono\":\"  \"").andExpect(status().isCreated())
				.andExpect(jsonPath("$.comuna.telefono").doesNotExist())
				.andExpect(jsonPath("$.comuna.whatsapp").doesNotExist());
	}

	@Test
	void altaConTelefonoInvalidoDa400ConElErrorEnElCampo() throws Exception {
		crear(",\"telefono\":\"abc12345678\"").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.telefono")
						.value("Escribí el número con el código de país 54, sin el 0 ni el 15 (por ejemplo 5493543123456)."));
		crear(",\"whatsapp\":\"1234567\"").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.whatsapp").exists());
		crear(",\"telefono\":\"1234567890123456\"").andExpect(status().isBadRequest());
	}

	// ---- PATCH

	@Test
	void patchCambiaTelefonoYWhatsappNormalizados() throws Exception {
		Comuna c = comuna("t76-cambia");
		modificar(c.getId(), "{\"telefono\":\"+54 351 555-1234\",\"whatsapp\":\"5493515551234\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.telefono").value("543515551234"))
				.andExpect(jsonPath("$.whatsapp").value("5493515551234"));
	}

	@Test
	void patchConTextoVacioBorraElDato() throws Exception {
		Comuna c = comuna("t76-borra");
		c.setTelefono("5493515551234");
		c.setWhatsapp("5493515551234");
		comunas.saveAndFlush(c);

		modificar(c.getId(), "{\"telefono\":\"\",\"whatsapp\":\"  \"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.telefono").doesNotExist())
				.andExpect(jsonPath("$.whatsapp").doesNotExist());
	}

	@Test
	void patchSinEsosCamposNoLosToca() throws Exception {
		Comuna c = comuna("t76-no-toca");
		c.setTelefono("5493515551234");
		c.setWhatsapp("5493515559999");
		comunas.saveAndFlush(c);

		modificar(c.getId(), "{\"nombre\":\"Solo nombre\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.telefono").value("5493515551234"))
				.andExpect(jsonPath("$.whatsapp").value("5493515559999"));
	}

	@Test
	void patchConTelefonoInvalidoDa400() throws Exception {
		Comuna c = comuna("t76-invalido");
		modificar(c.getId(), "{\"telefono\":\"llamame\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.telefono").exists());
		modificar(c.getId(), "{\"whatsapp\":\"1234567\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.whatsapp").exists());
	}

	@Test
	void soloAceptaNumerosArgentinosConCodigoDePais() throws Exception {
		Comuna c = comuna("t76-argentino");
		// Local con 0 y 15, sin código de país, de otro país o con el 15 después del 54.
		for (String malo : new String[] { "(0351) 555-1234", "03515551234", "3515551234", "341234567890", "54351555123",
				"5493515551234567" }) {
			modificar(c.getId(), "{\"telefono\":\"" + malo + "\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores.telefono").exists());
			crear(",\"whatsapp\":\"" + malo + "\"").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errores.whatsapp").exists());
		}
		// Fijo (54 + 10) y celular (549 + 10).
		modificar(c.getId(), "{\"telefono\":\"543515551234\",\"whatsapp\":\"5493515551234\"}")
				.andExpect(status().isOk());
	}

	@Test
	void patchConTelefonoDe16DigitosDa400() throws Exception {
		Comuna c = comuna("t76-16-digitos");
		modificar(c.getId(), "{\"telefono\":\"5493515551234567\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"));
	}

	@Test
	void patchNoCambiaElEnlaceNiLaProvincia() throws Exception {
		Comuna c = comuna("t76-fijos");
		modificar(c.getId(), "{\"slug\":\"otro-enlace\",\"provincia\":\"SALTA\",\"nombre\":\"Nuevo\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slug").value("t76-fijos"))
				.andExpect(jsonPath("$.provincia").value(Provincia.CORDOBA.getNombre()));
		Comuna guardada = comunas.findById(c.getId()).orElseThrow();
		assertThat(guardada.getSlug()).isEqualTo("t76-fijos");
		assertThat(guardada.getProvincia()).isEqualTo(Provincia.CORDOBA);
	}

	@Test
	void patchEnUnaComunaInexistenteDa404() throws Exception {
		modificar(987654321L, "{\"telefono\":\"5493515551234\"}").andExpect(status().isNotFound());
	}

	@Test
	void patchConDepartamentoVacioLoBorra() throws Exception {
		Comuna c = comuna("t76-depto");
		c.setDepartamento("Departamento Colón");
		comunas.saveAndFlush(c);
		modificar(c.getId(), "{\"departamento\":\"\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.departamento").doesNotExist());
	}

	@Test
	void patchSoloLoPuedeHacerElSuperadmin() throws Exception {
		Comuna c = comuna("t76-roles");
		int n = 0;
		for (Rol rol : new Rol[] { Rol.ADMIN_COMUNA, Rol.VECINO, Rol.ADMINISTRATIVO, Rol.AGENTE }) {
			Usuario u = new Usuario();
			u.setComuna(c);
			u.setCorreo("rol76-" + n++ + "@mail.com");
			u.setClaveHash("hash");
			u.setNombre("Nombre");
			u.setRol(rol);
			u.setActivo(true);
			String token = jwt.generar(usuarios.saveAndFlush(u));
			mockMvc.perform(patch(RUTA + "/" + c.getId()).header("Authorization", "Bearer " + token)
					.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Hackeada\"}"))
					.andExpect(status().isForbidden());
		}
		em.clear();
		assertThat(comunas.findById(c.getId()).orElseThrow().getNombre()).isEqualTo("Comuna de prueba");
	}

	// ---- actualizadaEn

	@Test
	void actualizadaEnAvanzaConElPatch() throws Exception {
		Comuna c = comuna("t76-fecha-patch");
		fijarFechaVieja(c.getId());

		assertThat(actualizadaEn(modificar(c.getId(), "{\"nombre\":\"Nombre nuevo\"}").andExpect(status().isOk())))
				.isAfter(FECHA_VIEJA);
	}

	@Test
	void actualizadaEnAvanzaAlDesactivar() throws Exception {
		Comuna c = comuna("t76-fecha-activa");
		fijarFechaVieja(c.getId());

		assertThat(actualizadaEn(modificar(c.getId(), "{\"activa\":false}").andExpect(status().isOk())))
				.isAfter(FECHA_VIEJA);
	}

	@Test
	void unPatchSinCambiosRealesNoTocaLaFecha() throws Exception {
		Comuna c = comuna("t76-fecha-igual");
		fijarFechaVieja(c.getId());

		assertThat(actualizadaEn(modificar(c.getId(), "{\"nombre\":\"Comuna de prueba\",\"activa\":true}")
				.andExpect(status().isOk()))).isEqualTo(FECHA_VIEJA);
	}

	@Test
	void actualizadaEnAvanzaAlSubirElLogo() throws Exception {
		Comuna c = comuna("t76-fecha-logo");
		fijarFechaVieja(c.getId());

		ResultActions respuesta = mockMvc.perform(multipart(HttpMethod.PUT, RUTA + "/" + c.getId() + "/logo")
				.file(new MockMultipartFile("archivo", "logo.png", "image/png", ImagenesDePrueba.png(100, 100)))
				.header("Authorization", "Bearer " + tokenSuperadmin)).andExpect(status().isOk());
		assertThat(actualizadaEn(respuesta)).isAfter(FECHA_VIEJA);
	}

	@Test
	void actualizadaEnAvanzaAlSubirElLimite() throws Exception {
		Comuna c = comuna("t76-fecha-limite");
		fijarFechaVieja(c.getId());

		ResultActions respuesta = mockMvc.perform(put(RUTA + "/" + c.getId() + "/limite")
				.header("Authorization", "Bearer " + tokenSuperadmin).contentType(MediaType.APPLICATION_JSON)
				.content(LimiteComunaTests.POLIGONO)).andExpect(status().isOk());
		assertThat(actualizadaEn(respuesta)).isAfter(FECHA_VIEJA);
	}

	// ---- Lista

	@Test
	void laListaDevuelveLosCamposNuevosSinLogoNiLimite() throws Exception {
		Comuna c = comuna("t76-lista");
		c.setTelefono("5493515551234");
		c.setWhatsapp("5493515559999");
		comunas.saveAndFlush(c);
		fijarFechaVieja(c.getId());

		String cuerpo = mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"logo\""))))
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("\"limite\""))))
				.andReturn().getResponse().getContentAsString();

		JsonNode fila = null;
		for (JsonNode f : json.readTree(cuerpo)) {
			if (f.get("id").asLong() == c.getId()) {
				fila = f;
			}
		}
		assertThat(fila).isNotNull();
		assertThat(fila.get("telefono").asText()).isEqualTo("5493515551234");
		assertThat(fila.get("whatsapp").asText()).isEqualTo("5493515559999");
		assertThat(fila.get("creadaEn").asText()).isNotBlank();
		assertThat(Instant.parse(fila.get("actualizadaEn").asText())).isEqualTo(FECHA_VIEJA);
	}
}
