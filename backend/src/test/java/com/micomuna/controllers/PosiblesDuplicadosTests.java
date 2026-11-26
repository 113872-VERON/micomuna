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
import java.math.RoundingMode;
import java.sql.Timestamp;
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

// MC-26: aviso de posibles duplicados cercanos en el detalle del panel y PUT .../no-es-duplicado. Contra la base de
// desarrollo, dentro de una transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PosiblesDuplicadosTests {

	private static final String RUTA = "/api/panel/reclamos";
	private static final double LAT = -31.420083;
	private static final double LNG = -64.188776;
	private static final double METROS_POR_GRADO = 111_194.93;

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
	private Usuario adminA;
	private Usuario administrativoA;
	private String tokenAdminA;
	private String tokenAdministrativoA;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t26-dup-a");
		comunaB = comuna("t26-dup-b");
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
		u.setCorreo("m26-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private static BigDecimal grados(double valor) {
		return BigDecimal.valueOf(valor).setScale(6, RoundingMode.HALF_UP);
	}

	// Reclamo en el punto base corrido "norte" metros al norte y "este" metros al este (con la longitud corregida por la latitud).
	private Reclamo reclamo(Comuna comuna, int numero, String tipo, double norte, double este) {
		Usuario vecino = comuna == comunaA ? vecinoA : usuario(comuna, Rol.VECINO, "Vecino B");
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo(tipo).orElseThrow());
		r.setDescripcion("Reclamo " + numero);
		r.setDireccion("Calle " + numero);
		r.setLatitud(grados(LAT + norte / METROS_POR_GRADO));
		r.setLongitud(grados(LNG + este / (METROS_POR_GRADO * Math.cos(Math.toRadians(LAT)))));
		r.setCreadoEn(Instant.now().minus(10, ChronoUnit.DAYS).plus(numero, ChronoUnit.HOURS));
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	// Bache a "norte" metros al norte del punto base.
	private Reclamo bache(int numero, double norte) {
		return reclamo(comunaA, numero, "BACHE", norte, 0);
	}

	private Reclamo sinUbicacion(int numero) {
		Reclamo r = new Reclamo();
		r.setComuna(comunaA);
		r.setNumero(numero);
		r.setVecino(vecinoA);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Sin ubicación " + numero);
		r.setReferencia("Frente a la plaza");
		r.setCreadoEn(Instant.now().minus(10, ChronoUnit.DAYS).plus(numero, ChronoUnit.HOURS));
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecinoA);
		return r;
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

	private void resolver(Reclamo r) {
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Listo"));
	}

	private void descartar(Reclamo r) {
		cambiar(r, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));
	}

	private ResultActions detalle(int numero) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(RUTA + "/" + numero).header("Authorization", "Bearer " + tokenAdminA));
	}

	private ResultActions marcar(int numero, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(put(RUTA + "/" + numero + "/no-es-duplicado").header("Authorization", "Bearer " + token));
	}

	private ResultActions marcar(int numero) throws Exception {
		return marcar(numero, tokenAdminA);
	}

	private static String sinRuta(String problema) {
		return problema.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private Timestamp actualizadoEn(Reclamo r) {
		return jdbc.queryForObject("select actualizado_en from reclamo where id = ?", Timestamp.class, r.getId());
	}

	private int filasDeHistorial(Reclamo r) {
		return jdbc.queryForObject("select count(*) from reclamo_estado where id_reclamo = ?", Integer.class, r.getId());
	}

	// ---- Candidatos

	@Test
	void unCandidatoA90mAparecePeroUnoA110mNo() throws Exception {
		bache(1, 0);
		bache(2, 90);
		bache(3, 110);

		detalle(1).andExpect(status().isOk())
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(1)))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].numero").value(2))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].distanciaMetros").value(90))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].direccion").value("Calle 2"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].descripcion").value("Reclamo 2"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].creadoEn").isNotEmpty())
				.andExpect(jsonPath("$.posiblesDuplicados.marcadoNuevoPor").value(nullValue()))
				.andExpect(jsonPath("$.posiblesDuplicados.marcadoNuevoEn").value(nullValue()));
		// Desde el de 90 m se ve el de 110 m (a 20 m) y el primero (a 90 m).
		detalle(2).andExpect(jsonPath("$.posiblesDuplicados.candidatos[*].numero", contains(3, 1)));
		// El de 110 m solo ve al de 90 m (a 20 m): el primero queda a 110 m, fuera del radio.
		detalle(3).andExpect(jsonPath("$.posiblesDuplicados.candidatos[*].numero", contains(2)));
	}

	@Test
	void laDistanciaEnLongitudUsaElCosenoDeLaLatitud() throws Exception {
		bache(1, 0);
		reclamo(comunaA, 2, "BACHE", 0, 90);
		reclamo(comunaA, 3, "BACHE", 0, -110);

		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(1)))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].numero").value(2))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].distanciaMetros").value(90));
	}

	@Test
	void soloCuentanLosDelMismoTipoYLosAbiertos() throws Exception {
		bache(1, 0);
		reclamo(comunaA, 2, "LUZ", 20, 0);
		Reclamo asignado = bache(3, 30);
		cambiar(asignado, CambioDeEstado.asignar(agenteA.getId()));
		Reclamo enReparacion = bache(4, 40);
		cambiar(enReparacion, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(enReparacion, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		resolver(bache(5, 50));
		descartar(bache(6, 60));

		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(2)))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].numero").value(3))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[1].numero").value(4))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[1].estado").value("EN_REPARACION"));
	}

	@Test
	void unReclamoDeOtraComunaNuncaAparece() throws Exception {
		bache(1, 0);
		reclamo(comunaB, 2, "BACHE", 5, 0);

		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
	}

	@Test
	void losReclamosSinUbicacionNoTienenCandidatosNiSonCandidatos() throws Exception {
		bache(1, 0);
		sinUbicacion(2);

		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
		detalle(2).andExpect(status().isOk()).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
		marcar(2).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SIN_CANDIDATOS"));
	}

	@Test
	void elMasCercanoVaPrimeroConTopeDeCincoYAIgualDistanciaElMasViejo() throws Exception {
		bache(1, 0);
		// Siete candidatos: a 10, 15, 15, 30, 40, 50 y 60 m; el de 50 y el de 60 quedan afuera por el tope de 5. Los dos de
		// 15 m empatan: va primero el más viejo (el 8, que se creó antes que el 9).
		bache(10, 10);
		bache(9, 15);
		bache(8, 15);
		bache(7, 30);
		bache(6, 40);
		bache(5, 50);
		bache(4, 60);

		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(5)))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[*].numero", contains(10, 8, 9, 7, 6)))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[*].distanciaMetros", contains(10, 15, 15, 30, 40)));
	}

	@Test
	void unReclamoCerradoNoTieneCandidatosYNoSonCandidatosDeNadie() throws Exception {
		Reclamo resuelto = bache(1, 0);
		Reclamo descartado = bache(2, 10);
		bache(3, 20);
		resolver(resuelto);
		descartar(descartado);

		detalle(1).andExpect(jsonPath("$.estado").value("RESUELTO"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
		detalle(2).andExpect(jsonPath("$.estado").value("DESCARTADO"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
		// El abierto no los ve a ellos.
		detalle(3).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
		marcar(1).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RECLAMO_CERRADO"));
		marcar(2).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RECLAMO_CERRADO"));
	}

	@Test
	void siSeCambiaElTipoLaBusquedaCambiaConEl() throws Exception {
		bache(1, 0);
		reclamo(comunaA, 2, "LUZ", 10, 0);

		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)));
		mockMvc.perform(put(RUTA + "/1/tipo").header("Authorization", "Bearer " + tokenAdminA).contentType("application/json")
				.content("{\"tipo\":\"LUZ\"}")).andExpect(status().isOk())
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].numero").value(2));
	}

	// ---- Marcar como nuevo

	@Test
	void marcarComoNuevoDevuelveElDetalleSinCandidatosConQuienYCuando() throws Exception {
		Reclamo r = bache(1, 0);
		bache(2, 50);

		marcar(1, tokenAdministrativoA).andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.estado").value("RECIBIDO"))
				.andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)))
				.andExpect(jsonPath("$.posiblesDuplicados.marcadoNuevoPor").value("Admin Tivo"))
				.andExpect(jsonPath("$.posiblesDuplicados.marcadoNuevoEn").isNotEmpty());

		// Queda registrado y el aviso no vuelve a aparecer.
		detalle(1).andExpect(jsonPath("$.posiblesDuplicados.candidatos", hasSize(0)))
				.andExpect(jsonPath("$.posiblesDuplicados.marcadoNuevoPor").value("Admin Tivo"));
		assertThat(((Number) jdbc.queryForObject("select id_usuario_no_duplicado from reclamo where id = ?", Number.class,
				r.getId())).longValue()).isEqualTo(administrativoA.getId());
		// El otro reclamo no se tocó: sigue viendo al primero.
		detalle(2).andExpect(jsonPath("$.posiblesDuplicados.candidatos[0].numero").value(1))
				.andExpect(jsonPath("$.posiblesDuplicados.marcadoNuevoPor").value(nullValue()));
	}

	@Test
	void marcarComoNuevoNoCambiaEstadoNiHistorialNiActualizadoEn() throws Exception {
		Reclamo r = bache(1, 0);
		bache(2, 50);
		Timestamp antes = actualizadoEn(r);
		int filas = filasDeHistorial(r);

		marcar(1).andExpect(status().isOk());

		assertThat(actualizadoEn(r)).isEqualTo(antes);
		assertThat(filasDeHistorial(r)).isEqualTo(filas);
		assertThat(jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId()))
				.isEqualTo("RECIBIDO");
	}

	@Test
	void marcarDosVecesDa409YaMarcadoYSinCandidatosDa409SinCandidatos() throws Exception {
		bache(1, 0);
		bache(2, 50);
		bache(3, 500);

		marcar(3).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("SIN_CANDIDATOS"));
		marcar(1).andExpect(status().isOk());
		marcar(1).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("YA_MARCADO"));
	}

	@Test
	void unReclamoYaMarcadoQueSeCierraDa409ReclamoCerradoAntesQueYaMarcado() throws Exception {
		Reclamo r = bache(1, 0);
		bache(2, 50);
		marcar(1).andExpect(status().isOk());
		descartar(r);

		marcar(1).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RECLAMO_CERRADO"));
	}

	@Test
	void unReclamoDeOtraComunaOInexistenteDa404Identico() throws Exception {
		reclamo(comunaB, 7, "BACHE", 0, 0);
		reclamo(comunaB, 8, "BACHE", 10, 0);

		String inexistente = marcar(9).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();
		String otraComuna = marcar(7).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

		assertThat(sinRuta(otraComuna)).isEqualTo(sinRuta(inexistente));
		assertThat(jdbc.queryForObject("select count(*) from reclamo where id_comuna = ? and no_duplicado_en is not null",
				Integer.class, comunaB.getId())).isZero();
		detalle(7).andExpect(status().isNotFound());
	}

	@Test
	void vecinoAgenteYSuperadminRecibenSiempre403YSinTokenDa401() throws Exception {
		bache(1, 0);
		bache(2, 50);
		// El superadmin es único en la base: dentro de esta transacción (que se deshace) se saca el que haya.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario superadmin = new Usuario();
		superadmin.setCorreo("m26-super@mail.com");
		superadmin.setNombre("Super");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setClaveHash("hash");
		superadmin.setActivo(true);
		usuarios.saveAndFlush(superadmin);

		for (String token : List.of(jwt.generar(vecinoA), jwt.generar(agenteA), jwt.generar(superadmin))) {
			marcar(1, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		}
		mockMvc.perform(put(RUTA + "/1/no-es-duplicado")).andExpect(status().isUnauthorized());
		assertThat(jdbc.queryForObject("select count(*) from reclamo where id_comuna = ? and no_duplicado_en is not null",
				Integer.class, comunaA.getId())).isZero();
	}

	// ---- El vecino y el agente no ven nada de esto

	@Test
	void elVecinoYElAgenteNoVenElAvisoNiLaMarca() throws Exception {
		Reclamo r = bache(1, 0);
		bache(2, 50);
		marcar(1).andExpect(status().isOk());
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		em.flush();
		em.clear();

		List<String> cuerpos = List.of(
				mockMvc.perform(get("/api/reclamos/mios/1").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
						.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				mockMvc.perform(get("/api/reclamos/mios").header("Authorization", "Bearer " + jwt.generar(vecinoA)))
						.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				mockMvc.perform(get("/api/agente/reclamos/1").header("Authorization", "Bearer " + jwt.generar(agenteA)))
						.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
				mockMvc.perform(get("/api/agente/reclamos").header("Authorization", "Bearer " + jwt.generar(agenteA)))
						.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		for (String cuerpo : cuerpos) {
			assertThat(cuerpo).doesNotContain("posiblesDuplicados", "candidatos", "marcadoNuevo", "noDuplicado", "Admin Comuna");
		}
	}
}
