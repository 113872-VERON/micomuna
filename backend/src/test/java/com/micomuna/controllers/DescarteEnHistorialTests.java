package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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

// MC-21: al descartar, la fila del historial guarda un resumen de por qué (interno: el panel lo ve, el vecino no).
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DescarteEnHistorialTests {

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

	private Comuna comuna;
	private Usuario vecino;
	private Usuario admin;
	private int contador;

	@BeforeEach
	void preparar() {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t21-descarte");
		comuna = comunas.saveAndFlush(c);
		vecino = usuario(Rol.VECINO);
		admin = usuario(Rol.ADMIN_COMUNA);
	}

	private Usuario usuario(Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo("d21-" + (++contador) + "@mail.com");
		u.setNombre("Usuario " + contador);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private Reclamo reclamo(int numero) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Reclamo " + numero);
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	private void cambiar(int numero, CambioDeEstado cambio) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
		try {
			cambios.cambiar(numero, cambio, admin.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	// La observación de la fila de historial que pasó a DESCARTADO.
	private String observacionDelDescarte(Reclamo r) {
		em.flush();
		return jdbc.queryForObject("select observacion from reclamo_estado where id_reclamo = ? and estado = 'DESCARTADO'",
				String.class, r.getId());
	}

	@Test
	void unDescarteIndividualGuardaElMotivoLegibleYLaObservacion() {
		Reclamo a = reclamo(1);
		Reclamo b = reclamo(2);
		Reclamo c = reclamo(3);
		Reclamo original = reclamo(4);

		cambiar(1, CambioDeEstado.descartar(MotivoDescarte.NO_ES_DE_LA_COMUNA, "  Es de la comuna vecina ", null));
		cambiar(2, CambioDeEstado.descartar(MotivoDescarte.NO_SE_PUEDE_ATENDER, "Requiere una obra mayor", null));
		cambiar(3, CambioDeEstado.descartar(MotivoDescarte.OTRO, "Era una broma", null));
		Reclamo duplicado = reclamo(5);
		cambiar(5, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, 4));
		// Con duplicado no se acepta una observación: 400 VALIDACION y no cambia nada.
		Reclamo conNota = reclamo(6);
		assertThatThrownBy(() -> cambiar(6, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, "Mismo pozo de la esquina", 4)))
				.isInstanceOf(com.micomuna.exceptions.PeticionInvalidaException.class)
				.satisfies(e -> assertThat(((com.micomuna.exceptions.PeticionInvalidaException) e).getCodigo()).isEqualTo("VALIDACION"));
		assertThat(conNota.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);

		assertThat(observacionDelDescarte(a)).isEqualTo("No es de la comuna: Es de la comuna vecina");
		assertThat(observacionDelDescarte(b)).isEqualTo("No se puede atender: Requiere una obra mayor");
		assertThat(observacionDelDescarte(c)).isEqualTo("Otro motivo: Era una broma");
		assertThat(observacionDelDescarte(duplicado)).isEqualTo("Duplicado del n.º 4");
		assertThat(original.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);
	}

	@Test
	void unDescarteEnLoteTambienGuardaElResumenEnCadaReclamo() throws Exception {
		Reclamo a = reclamo(1);
		Reclamo b = reclamo(2);
		em.flush();
		em.clear();

		mockMvc.perform(post("/api/panel/reclamos/descartes").header("Authorization", "Bearer " + jwt.generar(admin))
				.contentType("application/json")
				.content("{\"numeros\":[1,2],\"motivo\":\"NO_SE_PUEDE_ATENDER\",\"observacion\":\"Fuera de nuestra competencia\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.aplicados.length()").value(2));

		assertThat(observacionDelDescarte(a)).isEqualTo("No se puede atender: Fuera de nuestra competencia");
		assertThat(observacionDelDescarte(b)).isEqualTo("No se puede atender: Fuera de nuestra competencia");

		Reclamo original = reclamo(3);
		Reclamo duplicado = reclamo(4);
		em.flush();
		em.clear();
		mockMvc.perform(post("/api/panel/reclamos/descartes").header("Authorization", "Bearer " + jwt.generar(admin))
				.contentType("application/json").content("{\"numeros\":[4],\"motivo\":\"DUPLICADO\",\"numeroOriginal\":3}"))
				.andExpect(status().isOk());
		assertThat(observacionDelDescarte(duplicado)).isEqualTo("Duplicado del n.º 3");
		assertThat(original.getNumero()).isEqualTo(3);
	}

	@Test
	void elPanelVeElResumen_elVecinoNo_yAlReabrirElHistorialConservaElPorQue() throws Exception {
		Reclamo r = reclamo(1);
		cambiar(1, CambioDeEstado.descartar(MotivoDescarte.OTRO, "Era una broma", null));
		cambiar(1, CambioDeEstado.a(EstadoReclamo.RECIBIDO));
		em.flush();
		em.clear();

		// Reabierto: el panel sigue viendo por qué se descartó.
		mockMvc.perform(get("/api/panel/reclamos/1").header("Authorization", "Bearer " + jwt.generar(admin)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.historial[1].estado").value("DESCARTADO"))
				.andExpect(jsonPath("$.historial[1].observacion").value("Otro motivo: Era una broma"));

		// Descartado de nuevo: el vecino ve el descarte (motivo y observación del reclamo), pero la observación del historial
		// no viaja en su historial.
		cambiar(1, CambioDeEstado.descartar(MotivoDescarte.NO_ES_DE_LA_COMUNA, "Otra comuna", null));
		em.flush();
		em.clear();
		String delVecino = mockMvc.perform(get("/api/reclamos/mios/1").header("Authorization", "Bearer " + jwt.generar(vecino)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.historial[3].estado").value("DESCARTADO"))
				.andExpect(jsonPath("$.historial[3].observacion").doesNotExist()).andReturn().getResponse().getContentAsString();
		assertThat(delVecino).doesNotContain("Otro motivo: Era una broma").doesNotContain("No es de la comuna: Otra comuna");
		assertThat(r.getNumero()).isEqualTo(1);
	}

	@Test
	void elResumenSeRecortaA500() {
		String largo = "x".repeat(600);

		assertThat(CambioDeEstadoService.recortarParaElHistorial(largo)).hasSize(500).endsWith("…");
		assertThat(CambioDeEstadoService.recortarParaElHistorial("x".repeat(500))).hasSize(500).doesNotEndWith("…");
		assertThat(CambioDeEstadoService.recortarParaElHistorial("  ")).isNull();
		// El peor caso real: motivo más largo + observación máxima (300) entra sin recortar.
		String peor = CambioDeEstadoService.resumenDelDescarte(
				CambioDeEstado.descartar(MotivoDescarte.NO_SE_PUEDE_ATENDER, "y".repeat(300), null));
		assertThat(peor).hasSize("No se puede atender: ".length() + 300);
	}
}
