package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
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
import com.micomuna.exceptions.RecursoNoEncontradoException;
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

// MC-20 (subtarea MC-83): el agente de campo ve y resuelve sus reclamos. Contra la base de desarrollo, dentro de una
// transacción que se deshace al terminar cada prueba.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AgenteReclamosTests {

	private static final String RUTA = "/api/agente/reclamos";

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
	private Usuario agenteA;
	private Usuario otroAgenteA;
	private Usuario agenteB;
	private Usuario adminA;
	private Usuario administrativoA;
	private String tokenAgenteA;
	private String tokenOtroAgenteA;
	private int contadorUsuarios;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t20-agente-a");
		comunaB = comuna("t20-agente-b");
		vecinoA = usuario(comunaA, Rol.VECINO, "Vecina Ana Secreta");
		agenteA = usuario(comunaA, Rol.AGENTE, "Álvaro Agente");
		otroAgenteA = usuario(comunaA, Rol.AGENTE, "Zoe Agente");
		agenteB = usuario(comunaB, Rol.AGENTE, "Agente de B");
		adminA = usuario(comunaA, Rol.ADMIN_COMUNA, "Admin Comuna");
		administrativoA = usuario(comunaA, Rol.ADMINISTRATIVO, "Admin Tivo");
		tokenAgenteA = jwt.generar(agenteA);
		tokenOtroAgenteA = jwt.generar(otroAgenteA);
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
		u.setCorreo("m20-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Un reclamo de la comuna, creado hace `horas` horas (más horas = más viejo).
	private Reclamo reclamo(Comuna comuna, int numero, int horas) {
		Usuario vecino = comuna == comunaA ? vecinoA : usuario(comuna, Rol.VECINO, "Vecino B");
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Reclamo " + numero);
		r.setReferencia("Frente a la plaza");
		r.setDireccion("Calle Falsa 123");
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setCreadoEn(Instant.now().minus(horas, ChronoUnit.HOURS));
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	private Reclamo reclamo(int numero) {
		return reclamo(comunaA, numero, 100 - numero);
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

	private Reclamo asignado(int numero, Usuario agente) {
		Reclamo r = reclamo(numero);
		cambiar(r, CambioDeEstado.asignar(agente.getId()));
		return r;
	}

	private Reclamo enReparacion(int numero, Usuario agente) {
		Reclamo r = asignado(numero, agente);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		return r;
	}

	private ReclamoFoto foto(Reclamo r, OrigenFoto origen, int orden, String tipo) {
		return fotos.save(new ReclamoFoto(r, origen, orden, ImagenesDePrueba.png(8, 8), tipo));
	}

	private ResultActions pedir(String ruta, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(ruta).header("Authorization", "Bearer " + token));
	}

	private ResultActions empezar(int numero, String token) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(post(RUTA + "/" + numero + "/empezar").header("Authorization", "Bearer " + token));
	}

	private ResultActions resolver(int numero, String token, String nota, MockMultipartFile foto) throws Exception {
		em.flush();
		em.clear();
		MockMultipartHttpServletRequestBuilder pedido = multipart(RUTA + "/" + numero + "/resolver");
		if (nota != null) {
			pedido.param("nota", nota);
		}
		if (foto != null) {
			pedido.file(foto);
		}
		return mockMvc.perform(pedido.header("Authorization", "Bearer " + token));
	}

	private static MockMultipartFile fotoDelTrabajo(byte[] bytes) {
		return new MockMultipartFile("foto", "trabajo.jpg", "image/jpeg", bytes);
	}

	private static String sinRuta(String problema) {
		return problema.replaceAll("\"instance\":\"[^\"]*\",?", "");
	}

	private String estadoDe(Reclamo r) {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId());
	}

	private List<String> historial(Reclamo r) {
		return jdbc.queryForList(
				"select nvl(estado_anterior, '-') || '>' || estado || '@usuario=' || nvl(to_char(id_usuario), '-') || '@agente=' || nvl(to_char(id_agente), '-') from reclamo_estado where id_reclamo = ? order by id",
				String.class, r.getId());
	}

	private int fotosDeTrabajo(Reclamo r) {
		return jdbc.queryForObject("select count(*) from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'",
				Integer.class, r.getId());
	}

	private String notaDe(Reclamo r) {
		return jdbc.queryForObject("select trabajo_realizado from reclamo where id = ?", String.class, r.getId());
	}

	// ---- Roles

	@Test
	void soloElAgenteEntra_losDemasRolesRecibenSiempre403() throws Exception {
		Reclamo r = enReparacion(1, agenteA);
		foto(r, OrigenFoto.VECINO, 1, "image/png");
		jdbc.update("delete from usuario where rol = 'SUPERADMIN'");
		Usuario superadmin = new Usuario();
		superadmin.setCorreo("root-20@mail.com");
		superadmin.setNombre("Root");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setClaveHash("hash");
		superadmin.setActivo(true);
		usuarios.saveAndFlush(superadmin);

		for (String token : List.of(jwt.generar(vecinoA), jwt.generar(administrativoA), jwt.generar(adminA),
				jwt.generar(superadmin))) {
			pedir(RUTA, token).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			pedir(RUTA + "?grupo=cualquiera", token).andExpect(status().isForbidden());
			pedir(RUTA + "/1", token).andExpect(status().isForbidden());
			pedir(RUTA + "/1/fotos/1", token).andExpect(status().isForbidden());
			empezar(1, token).andExpect(status().isForbidden());
			resolver(1, token, "x".repeat(2000), null).andExpect(status().isForbidden());
		}
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		assertThat(estadoDe(r)).isEqualTo("EN_REPARACION");
	}

	// ---- Listas

	@Test
	void paraHacerTrae_losMiosEnAsignadoOEnReparacion_delMasViejoAlMasNuevo() throws Exception {
		// Números al revés de la antigüedad: el más viejo (n.º 3) tiene que ir primero.
		Reclamo r1 = reclamo(comunaA, 1, 10);
		Reclamo r2 = reclamo(comunaA, 2, 20);
		Reclamo r3 = reclamo(comunaA, 3, 30);
		cambiar(r1, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r2, CambioDeEstado.asignar(agenteA.getId()));
		cambiar(r2, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r3, CambioDeEstado.asignar(agenteA.getId()));
		// Lo que no entra: resuelto, de otro agente, sin asignar, descartado.
		Reclamo resuelto = enReparacion(4, agenteA);
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));
		asignado(5, otroAgenteA);
		reclamo(6);
		Reclamo descartado = reclamo(7);
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));

		String cuerpo = pedir(RUTA, tokenAgenteA).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.pagina").value(0))
				.andExpect(jsonPath("$.tamanio").value(50))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(3, 2, 1)))
				.andExpect(jsonPath("$.reclamos[*].estado", contains("ASIGNADO", "EN_REPARACION", "ASIGNADO")))
				.andExpect(jsonPath("$.reclamos[0].tipo").value("BACHE"))
				.andExpect(jsonPath("$.reclamos[0].tipoNombre").exists())
				.andExpect(jsonPath("$.reclamos[0].direccion").value("Calle Falsa 123"))
				.andExpect(jsonPath("$.reclamos[0].referencia").value("Frente a la plaza"))
				.andExpect(jsonPath("$.reclamos[0].latitud").value(-31.420083))
				.andExpect(jsonPath("$.reclamos[0].longitud").value(-64.188776))
				.andExpect(jsonPath("$.reclamos[0].creadoEn").exists())
				.andExpect(jsonPath("$.reclamos[0].estadoCambiadoEn").exists())
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("Vecina").doesNotContain("Secreta").doesNotContain("@mail.com");

		// Es lo mismo con el grupo escrito, y el de otro agente ve solo el suyo.
		pedir(RUTA + "?grupo=PARA_HACER", tokenAgenteA).andExpect(jsonPath("$.total").value(3));
		pedir(RUTA, tokenOtroAgenteA).andExpect(jsonPath("$.reclamos[*].numero", contains(5)));
	}

	@Test
	void resueltosTrae_losMiosResueltos_delResueltoMasRecienteAlMasViejo() throws Exception {
		Reclamo a = enReparacion(1, agenteA);
		Reclamo b = enReparacion(2, agenteA);
		Reclamo c = enReparacion(3, agenteA);
		for (Reclamo r : List.of(a, b, c)) {
			cambiar(r, CambioDeEstado.resolver("Listo " + r.getNumero()));
		}
		// Fechas de resolución explícitas: el n.º 2 se resolvió primero, después el 3 y al final el 1.
		fecharResolucion(b, 3);
		fecharResolucion(c, 2);
		fecharResolucion(a, 1);
		Reclamo ajeno = enReparacion(4, otroAgenteA);
		cambiar(ajeno, CambioDeEstado.resolver("De otro"));
		enReparacion(5, agenteA);

		pedir(RUTA + "?grupo=RESUELTOS", tokenAgenteA).andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(1, 3, 2)))
				.andExpect(jsonPath("$.reclamos[0].estado").value("RESUELTO"));
		pedir(RUTA + "?grupo=RESUELTOS&pagina=1&tamanio=2", tokenAgenteA)
				.andExpect(jsonPath("$.total").value(3))
				.andExpect(jsonPath("$.pagina").value(1))
				.andExpect(jsonPath("$.tamanio").value(2))
				.andExpect(jsonPath("$.reclamos[*].numero", contains(2)));
	}

	private void fecharResolucion(Reclamo r, int diasAtras) {
		em.flush();
		jdbc.update("update reclamo_estado set cambiado_en = ? where id_reclamo = ? and estado = 'RESUELTO'",
				Timestamp.from(Instant.now().minus(diasAtras, ChronoUnit.DAYS)), r.getId());
	}

	@Test
	void unGrupoOUnaPaginaInvalidosDan400() throws Exception {
		pedir(RUTA + "?grupo=OTRO", tokenAgenteA).andExpect(status().isBadRequest());
		pedir(RUTA + "?tamanio=0", tokenAgenteA).andExpect(status().isBadRequest());
		pedir(RUTA + "?tamanio=101", tokenAgenteA).andExpect(status().isBadRequest());
		pedir(RUTA + "?pagina=-1", tokenAgenteA).andExpect(status().isBadRequest());
		pedir(RUTA + "?pagina=1001", tokenAgenteA).andExpect(status().isBadRequest());
	}

	@Test
	void unAgenteSinReclamosRecibeUnaListaVacia() throws Exception {
		pedir(RUTA, tokenAgenteA).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.reclamos", hasSize(0)));
	}

	// ---- Detalle y aislamiento

	@Test
	void elDetalleTraeLoQueElAgenteNecesitaYNadaDelVecino() throws Exception {
		Reclamo r = asignado(1, agenteA);
		foto(r, OrigenFoto.VECINO, 1, "image/png");
		foto(r, OrigenFoto.TRABAJO, 1, "image/png");

		String cuerpo = pedir(RUTA + "/1", tokenAgenteA).andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.estado").value("ASIGNADO"))
				.andExpect(jsonPath("$.descripcion").value("Reclamo 1"))
				.andExpect(jsonPath("$.referencia").value("Frente a la plaza"))
				.andExpect(jsonPath("$.direccion").value("Calle Falsa 123"))
				.andExpect(jsonPath("$.latitud").value(-31.420083))
				.andExpect(jsonPath("$.tipo").value("BACHE"))
				// Las del trabajo no se muestran hasta que está RESUELTO.
				.andExpect(jsonPath("$.fotos", hasSize(1)))
				.andExpect(jsonPath("$.fotos[0].origen").value("VECINO"))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue()))
				.andReturn().getResponse().getContentAsString();
		assertThat(cuerpo).doesNotContain("Vecina").doesNotContain("Secreta").doesNotContain("@mail.com")
				.doesNotContain("vecino").doesNotContain("telefono").doesNotContain("correo");
	}

	@Test
	void unReclamoAjenoDeOtraComunaInexistenteODescartadoDa404Identico() throws Exception {
		asignado(1, otroAgenteA); // de otro agente de la misma comuna
		Reclamo deB = reclamo(comunaB, 2, 5);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(usuario(comunaB, Rol.ADMIN_COMUNA, "Admin B").getId(), Rol.ADMIN_COMUNA, comunaB.getId()),
				null, List.of()));
		try {
			cambios.cambiar(deB.getNumero(), CambioDeEstado.asignar(agenteB.getId()),
					((UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).id());
		} finally {
			SecurityContextHolder.clearContext();
		}
		Reclamo descartado = asignado(3, agenteA);
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));
		reclamo(4); // RECIBIDO, sin agente
		foto(descartado, OrigenFoto.VECINO, 1, "image/png");

		String inexistente = pedir(RUTA + "/99", tokenAgenteA).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO")).andReturn().getResponse().getContentAsString();
		for (int numero : new int[] { 1, 2, 3, 4 }) {
			String cuerpo = pedir(RUTA + "/" + numero, tokenAgenteA).andExpect(status().isNotFound()).andReturn().getResponse()
					.getContentAsString();
			assertThat(sinRuta(cuerpo)).as("detalle " + numero).isEqualTo(sinRuta(inexistente));
			empezar(numero, tokenAgenteA).andExpect(status().isNotFound());
			resolver(numero, tokenAgenteA, "nota", null).andExpect(status().isNotFound());
		}
		// Tampoco entra por la foto: cada intento da el mismo 404.
		Long idFoto = jdbc.queryForObject("select id from reclamo_foto where id_reclamo = ?", Long.class, descartado.getId());
		pedir(RUTA + "/3/fotos/" + idFoto, tokenAgenteA).andExpect(status().isNotFound());
		assertThat(estadoDe(descartado)).isEqualTo("DESCARTADO");
	}

	// ---- Fotos

	@Test
	void laFotoSeSirveConTipoGuardadoYCabecerasSeguras_yLasDelTrabajoSoloConElReclamoResuelto() throws Exception {
		Reclamo r = enReparacion(1, agenteA);
		ReclamoFoto delVecino = foto(r, OrigenFoto.VECINO, 1, "image/png");
		ReclamoFoto delTrabajo = foto(r, OrigenFoto.TRABAJO, 1, "image/png");

		pedir(RUTA + "/1/fotos/" + delVecino.getId(), tokenAgenteA).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(header().doesNotExist("Content-Disposition"));
		// En reparación, la del trabajo (resto de una resolución anterior) no se muestra.
		pedir(RUTA + "/1/fotos/" + delTrabajo.getId(), tokenAgenteA).andExpect(status().isNotFound());
		// Otro agente, ni la del vecino.
		pedir(RUTA + "/1/fotos/" + delVecino.getId(), tokenOtroAgenteA).andExpect(status().isNotFound());
		// Con otro número de reclamo tampoco.
		pedir(RUTA + "/2/fotos/" + delVecino.getId(), tokenAgenteA).andExpect(status().isNotFound());

		cambiar(r, CambioDeEstado.resolver(null));
		pedir(RUTA + "/1/fotos/" + delTrabajo.getId(), tokenAgenteA).andExpect(status().isOk());
	}

	// ---- Empezar

	@Test
	void empezarPasaDeAsignadoAEnReparacionYQuedaEnElHistorialConElAgenteDelToken() throws Exception {
		Reclamo r = asignado(1, agenteA);

		empezar(1, tokenAgenteA).andExpect(status().isOk())
				.andExpect(jsonPath("$.numero").value(1))
				.andExpect(jsonPath("$.estado").value("EN_REPARACION"));

		assertThat(estadoDe(r)).isEqualTo("EN_REPARACION");
		assertThat(historial(r)).containsExactly(
				"-" + ">RECIBIDO@usuario=" + vecinoA.getId() + "@agente=-",
				"RECIBIDO>ASIGNADO@usuario=" + adminA.getId() + "@agente=" + agenteA.getId(),
				"ASIGNADO>EN_REPARACION@usuario=" + agenteA.getId() + "@agente=-");
	}

	@Test
	void empezarFueraDeAsignadoDa409TransicionInvalidaSinTocarNada() throws Exception {
		Reclamo enReparacion = enReparacion(1, agenteA);
		Reclamo resuelto = enReparacion(2, agenteA);
		cambiar(resuelto, CambioDeEstado.resolver("Listo"));

		for (int numero : new int[] { 1, 2 }) {
			empezar(numero, tokenAgenteA).andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		}
		assertThat(estadoDe(enReparacion)).isEqualTo("EN_REPARACION");
		// Un resuelto no se reabre desde acá.
		assertThat(estadoDe(resuelto)).isEqualTo("RESUELTO");
		assertThat(historial(resuelto)).hasSize(4);
	}

	// ---- Resolver

	@Test
	void resolverConNotaYFotoGuardaTodoYElVecinoLoVeDespues() throws Exception {
		Reclamo r = enReparacion(1, agenteA);
		byte[] original = ImagenesDePrueba.conExif(ImagenesDePrueba.jpeg(64, 48), 1, "UBICACION-SECRETA-GPS");

		resolver(1, tokenAgenteA, "  Tapamos el pozo con asfalto  ", fotoDelTrabajo(original)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RESUELTO"))
				.andExpect(jsonPath("$.trabajoRealizado").value("Tapamos el pozo con asfalto"))
				.andExpect(jsonPath("$.fotos[*].origen", contains("TRABAJO")));

		assertThat(estadoDe(r)).isEqualTo("RESUELTO");
		assertThat(notaDe(r)).isEqualTo("Tapamos el pozo con asfalto");
		Map<String, Object> guardada = jdbc.queryForMap(
				"select orden, imagen_tipo, imagen from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'", r.getId());
		assertThat(((Number) guardada.get("ORDEN")).intValue()).isEqualTo(1);
		assertThat(guardada.get("IMAGEN_TIPO")).isEqualTo("image/jpeg");
		// Se volvió a armar: no conserva los datos ocultos del archivo original.
		assertThat(new String((byte[]) guardada.get("IMAGEN"), StandardCharsets.ISO_8859_1)).doesNotContain("UBICACION-SECRETA-GPS");
		assertThat(historial(r)).last().isEqualTo("EN_REPARACION>RESUELTO@usuario=" + agenteA.getId() + "@agente=-");

		// El vecino ve la nota y la foto del trabajo.
		Long idFoto = jdbc.queryForObject("select id from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'", Long.class,
				r.getId());
		pedir("/api/reclamos/mios/1", jwt.generar(vecinoA)).andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("RESUELTO"))
				.andExpect(jsonPath("$.trabajoRealizado").value("Tapamos el pozo con asfalto"))
				.andExpect(jsonPath("$.fotos[*].origen", contains("TRABAJO")));
		pedir("/api/reclamos/mios/1/fotos/" + idFoto, jwt.generar(vecinoA)).andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/jpeg"));
	}

	@Test
	void resolverSinNotaNiFotoValeYDejaLaNotaVacia() throws Exception {
		Reclamo r = enReparacion(1, agenteA);

		resolver(1, tokenAgenteA, null, null).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RESUELTO"))
				.andExpect(jsonPath("$.trabajoRealizado").value(nullValue())).andExpect(jsonPath("$.fotos", hasSize(0)));

		assertThat(notaDe(r)).isNull();
		assertThat(fotosDeTrabajo(r)).isZero();
	}

	@Test
	void unaNotaEnBlancoSeGuardaComoNull_yUnaFotoSinNota_tambienVale() throws Exception {
		Reclamo en = enReparacion(1, agenteA);
		Reclamo otro = enReparacion(2, agenteA);

		resolver(1, tokenAgenteA, "   ", null).andExpect(status().isOk());
		resolver(2, tokenAgenteA, null, fotoDelTrabajo(ImagenesDePrueba.png(20, 20))).andExpect(status().isOk());

		assertThat(notaDe(en)).isNull();
		assertThat(notaDe(otro)).isNull();
		assertThat(jdbc.queryForObject("select imagen_tipo from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'",
				String.class, otro.getId())).isEqualTo("image/png");
	}

	@Test
	void unCampoDeArchivoVacioYSinNombreEsSinFoto() throws Exception {
		Reclamo r = enReparacion(1, agenteA);

		resolver(1, tokenAgenteA, "Listo", new MockMultipartFile("foto", "", "application/octet-stream", new byte[0]))
				.andExpect(status().isOk());

		assertThat(estadoDe(r)).isEqualTo("RESUELTO");
		assertThat(fotosDeTrabajo(r)).isZero();
	}

	@Test
	void unaFotoInvalidaDa400YNoCambiaNada() throws Exception {
		Reclamo r = enReparacion(1, agenteA);
		int filas = historial(r).size();
		byte[] muyPesada = new byte[2 * 1024 * 1024 + 1];

		for (MockMultipartFile mala : List.of(
				new MockMultipartFile("foto", "x.jpg", "image/jpeg", "no soy una imagen".getBytes(StandardCharsets.UTF_8)),
				new MockMultipartFile("foto", "x.jpg", "image/jpeg", ImagenesDePrueba.gif(10, 10)),
				new MockMultipartFile("foto", "x.jpg", "image/jpeg", muyPesada),
				new MockMultipartFile("foto", "x.jpg", "image/jpeg", ImagenesDePrueba.png(2561, 10)),
				new MockMultipartFile("foto", "x.jpg", "image/jpeg", new byte[0]))) {
			resolver(1, tokenAgenteA, "Una nota", mala).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("FOTO_INVALIDA"));
		}
		assertThat(estadoDe(r)).isEqualTo("EN_REPARACION");
		assertThat(notaDe(r)).isNull();
		assertThat(fotosDeTrabajo(r)).isZero();
		assertThat(historial(r)).hasSize(filas);
	}

	@Test
	void unaNotaDeMasDe1000CaracteresDa400Validacion_yDe1000Vale() throws Exception {
		Reclamo r = enReparacion(1, agenteA);

		resolver(1, tokenAgenteA, "a".repeat(1001), null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION")).andExpect(jsonPath("$.errores.nota").exists());
		assertThat(estadoDe(r)).isEqualTo("EN_REPARACION");

		resolver(1, tokenAgenteA, "a".repeat(1000), null).andExpect(status().isOk());
		assertThat(notaDe(r)).hasSize(1000);
	}

	@Test
	void resolverFueraDeEnReparacionDa409TransicionInvalida_yNoDejaFoto() throws Exception {
		Reclamo asignado = asignado(1, agenteA);
		Reclamo resuelto = enReparacion(2, agenteA);
		cambiar(resuelto, CambioDeEstado.resolver("Ya estaba"));

		for (int numero : new int[] { 1, 2 }) {
			resolver(numero, tokenAgenteA, "Otra nota", fotoDelTrabajo(ImagenesDePrueba.jpeg(30, 30))).andExpect(status().isConflict())
					.andExpect(jsonPath("$.codigo").value("TRANSICION_INVALIDA"));
		}
		assertThat(estadoDe(asignado)).isEqualTo("ASIGNADO");
		assertThat(fotosDeTrabajo(asignado)).isZero();
		assertThat(fotosDeTrabajo(resuelto)).isZero();
		assertThat(notaDe(resuelto)).isEqualTo("Ya estaba");
	}

	@Test
	void alResolverDeNuevoUnReclamoReabiertoLaFotoDelTrabajoSeReemplaza() throws Exception {
		Reclamo r = enReparacion(1, agenteA);
		foto(r, OrigenFoto.VECINO, 1, "image/png");
		byte[] primera = ImagenesDePrueba.jpeg(40, 40);
		byte[] segunda = ImagenesDePrueba.png(50, 30);

		resolver(1, tokenAgenteA, "Primera vez", fotoDelTrabajo(primera)).andExpect(status().isOk());
		Long idPrimera = jdbc.queryForObject("select id from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'", Long.class,
				r.getId());
		// Lo reabre el personal (RESUELTO -> EN_REPARACION).
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));

		resolver(1, tokenAgenteA, "Segunda vez", fotoDelTrabajo(segunda)).andExpect(status().isOk())
				.andExpect(jsonPath("$.trabajoRealizado").value("Segunda vez"))
				.andExpect(jsonPath("$.fotos[*].origen", contains("VECINO", "TRABAJO")));

		assertThat(fotosDeTrabajo(r)).isEqualTo(1);
		Map<String, Object> nueva = jdbc.queryForMap(
				"select id, orden, imagen_tipo from reclamo_foto where id_reclamo = ? and origen = 'TRABAJO'", r.getId());
		assertThat(((Number) nueva.get("ID")).longValue()).isNotEqualTo(idPrimera);
		assertThat(((Number) nueva.get("ORDEN")).intValue()).isEqualTo(1);
		assertThat(nueva.get("IMAGEN_TIPO")).isEqualTo("image/png");
		// La del vecino sigue ahí.
		assertThat(jdbc.queryForObject("select count(*) from reclamo_foto where id_reclamo = ? and origen = 'VECINO'", Integer.class,
				r.getId())).isEqualTo(1);

		// Una tercera resolución sin foto no deja la foto vieja con una nota nueva.
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		resolver(1, tokenAgenteA, null, null).andExpect(status().isOk());
		assertThat(fotosDeTrabajo(r)).isZero();
		assertThat(notaDe(r)).isNull();
	}

	// ---- El control bajo el bloqueo

	@Test
	void elServicioVuelveAComprobarQueSeaDelAgenteConLaFilaBloqueada() {
		Reclamo r = enReparacion(1, agenteA);
		em.flush();
		em.clear();
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(otroAgenteA.getId(), Rol.AGENTE, comunaA.getId()), null, List.of()));
		try {
			assertThatThrownBy(() -> cambios.cambiar(1, CambioDeEstado.resolver("x"), otroAgenteA.getId(), otroAgenteA.getId(),
					EstadoReclamo.EN_REPARACION)).isInstanceOf(RecursoNoEncontradoException.class);
		} finally {
			SecurityContextHolder.clearContext();
		}
		assertThat(estadoDe(r)).isEqualTo("EN_REPARACION");
	}

	// ---- Historial del vecino y nota interna

	@Test
	void elVecinoNoVeLasFilasSinCambioDeEstadoNiLaNotaInternaDelHistorial() throws Exception {
		Reclamo r = reclamo(1);
		cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		// Reasignaciones ASIGNADO -> ASIGNADO: para el vecino no pasó nada nuevo.
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(adminA.getId(), Rol.ADMIN_COMUNA, comunaA.getId()), null, List.of()));
		try {
			cambios.asignarAgente(1, otroAgenteA.getId(), adminA.getId());
			cambios.asignarAgente(1, agenteA.getId(), adminA.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		resolver(1, tokenAgenteA, "Se tapó con asfalto", null).andExpect(status().isOk());

		String delVecino = pedir("/api/reclamos/mios/1", jwt.generar(vecinoA)).andExpect(status().isOk())
				.andExpect(jsonPath("$.historial[*].estado", contains("RECIBIDO", "ASIGNADO", "EN_REPARACION", "RESUELTO")))
				.andExpect(jsonPath("$.trabajoRealizado").value("Se tapó con asfalto"))
				.andReturn().getResponse().getContentAsString();
		assertThat(delVecino).doesNotContain("observacion").doesNotContain("usuario").doesNotContain("agente\"");
		// El personal, en la base, conserva todas las filas (incluidas las reasignaciones).
		assertThat(historial(r)).hasSize(6);
	}

	@Test
	void alResolverLaNotaQuedaTambienEnLaObservacionDelHistorialCortadaA500() throws Exception {
		Reclamo corta = enReparacion(1, agenteA);
		Reclamo larga = enReparacion(2, agenteA);
		Reclamo sinNota = enReparacion(3, agenteA);
		String notaLarga = "b".repeat(1000);

		resolver(1, tokenAgenteA, "  Se tapó el pozo ", null).andExpect(status().isOk());
		resolver(2, tokenAgenteA, notaLarga, null).andExpect(status().isOk())
				.andExpect(jsonPath("$.trabajoRealizado").value(notaLarga));
		resolver(3, tokenAgenteA, "  ", null).andExpect(status().isOk());

		assertThat(observacionDeResolucion(corta)).isEqualTo("Se tapó el pozo");
		assertThat(notaDe(corta)).isEqualTo("Se tapó el pozo");
		assertThat(observacionDeResolucion(larga)).hasSize(500).endsWith("…");
		assertThat(notaDe(larga)).hasSize(1000);
		assertThat(observacionDeResolucion(sinNota)).isNull();

		// El personal la ve en el historial del panel.
		pedir("/api/panel/reclamos/1", jwt.generar(adminA)).andExpect(status().isOk())
				.andExpect(jsonPath("$.historial[3].estado").value("RESUELTO"))
				.andExpect(jsonPath("$.historial[3].observacion").value("Se tapó el pozo"));
	}

	@Test
	void laNotaDeUnaResolucionAnteriorNoSePierdeAlReabrirYResolverDeNuevo() throws Exception {
		Reclamo r = enReparacion(1, agenteA);
		resolver(1, tokenAgenteA, "Primera nota", null).andExpect(status().isOk());
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		resolver(1, tokenAgenteA, "Segunda nota", null).andExpect(status().isOk());

		assertThat(jdbc.queryForList(
				"select observacion from reclamo_estado where id_reclamo = ? and estado = 'RESUELTO' order by id",
				String.class, r.getId())).containsExactly("Primera nota", "Segunda nota");
		assertThat(notaDe(r)).isEqualTo("Segunda nota");
	}

	private String observacionDeResolucion(Reclamo r) {
		return jdbc.queryForObject("select observacion from reclamo_estado where id_reclamo = ? and estado = 'RESUELTO'",
				String.class, r.getId());
	}
}
