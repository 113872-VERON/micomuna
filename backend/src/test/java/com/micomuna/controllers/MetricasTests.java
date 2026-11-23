package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.constants.Fechas;
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

// MC-21: métricas simples del panel. "Hoy" está fijo (miércoles 25/11/2026, 10:00 hora argentina) para que el rango por
// defecto sea determinista. Los días son de calendario argentino (UTC-3): las pruebas usan reclamos a un segundo de cada
// borde, que en UTC caerían del otro lado.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MetricasTests {

	@TestConfiguration
	static class RelojFijo {
		@Bean
		@Primary
		Clock relojDePrueba() {
			return Clock.fixed(Instant.parse("2026-11-25T13:00:00Z"), Fechas.ARGENTINA);
		}
	}

	private static final String RUTA = "/api/panel/metricas";

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
	private Usuario vecinoB;
	private Usuario agenteA;
	private Usuario adminA;
	private Usuario administrativoA;
	private Usuario adminB;
	private int contadorUsuarios;
	private int contadorReclamos;

	@BeforeEach
	void preparar() {
		comunaA = comuna("t21-metricas-a");
		comunaB = comuna("t21-metricas-b");
		vecinoA = usuario(comunaA, Rol.VECINO);
		vecinoB = usuario(comunaB, Rol.VECINO);
		agenteA = usuario(comunaA, Rol.AGENTE);
		adminA = usuario(comunaA, Rol.ADMIN_COMUNA);
		administrativoA = usuario(comunaA, Rol.ADMINISTRATIVO);
		adminB = usuario(comunaB, Rol.ADMIN_COMUNA);
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
		u.setCorreo("m21-" + (++contadorUsuarios) + "@mail.com");
		u.setNombre("Usuario " + contadorUsuarios);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Un instante a partir de una fecha y hora argentinas, por ejemplo "2026-11-02T00:00".
	private static Instant ba(String fechaHora) {
		return LocalDateTime.parse(fechaHora).atZone(Fechas.ARGENTINA).toInstant();
	}

	private Reclamo reclamo(Comuna comuna, String tipo, Instant creadoEn) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(++contadorReclamos);
		Usuario vecino = comuna == comunaA ? vecinoA : vecinoB;
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo(tipo).orElseThrow());
		r.setDescripcion("Reclamo " + contadorReclamos);
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setCreadoEn(creadoEn);
		reclamos.saveAndFlush(r);
		cambios.registrarAlta(r, vecino);
		return r;
	}

	private Reclamo reclamo(String tipo, String creadoEnBa) {
		return reclamo(comunaA, tipo, ba(creadoEnBa));
	}

	private void cambiar(Reclamo r, CambioDeEstado cambio) {
		Comuna comuna = r.getComuna();
		Usuario admin = comuna == comunaA ? adminA : adminB;
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
		try {
			cambios.cambiar(r.getNumero(), cambio, admin.getId());
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	// La fecha de la última fila del historial, escrita como la guarda la aplicación (hora UTC).
	private void fecharUltimoCambio(Reclamo r, String fechaHoraBa) {
		em.flush();
		jdbc.update("update reclamo_estado set cambiado_en = ? where id = (select max(id) from reclamo_estado where id_reclamo = ?)",
				Timestamp.valueOf(LocalDateTime.ofInstant(ba(fechaHoraBa), ZoneOffset.UTC)), r.getId());
	}

	// RECIBIDO -> ASIGNADO -> EN_REPARACION -> RESUELTO, con la resolución en la fecha dada.
	private void resolver(Reclamo r, String resueltoBa) {
		if (r.getEstado() == EstadoReclamo.RECIBIDO) {
			cambiar(r, CambioDeEstado.asignar(agenteA.getId()));
		}
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		cambiar(r, CambioDeEstado.resolver("Listo"));
		fecharUltimoCambio(r, resueltoBa);
	}

	private void reabrir(Reclamo r) {
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
	}

	private ResultActions pedir(String consulta, Usuario quien) throws Exception {
		em.flush();
		em.clear();
		return mockMvc.perform(get(RUTA + consulta).header("Authorization", "Bearer " + jwt.generar(quien)));
	}

	private ResultActions pedir(String consulta) throws Exception {
		return pedir(consulta, adminA);
	}

	// ---- Roles

	@Test
	void soloElPersonalAdministrativoYElAdminDeLaComunaEntran() throws Exception {
		jdbc.update("delete from usuario where rol = 'SUPERADMIN'");
		Usuario superadmin = new Usuario();
		superadmin.setCorreo("root-21@mail.com");
		superadmin.setNombre("Root");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setClaveHash("hash");
		superadmin.setActivo(true);
		usuarios.saveAndFlush(superadmin);

		for (Usuario quien : List.of(vecinoA, agenteA, superadmin)) {
			pedir("", quien).andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
			// 403 antes de validar los parámetros.
			pedir("?desde=basura", quien).andExpect(status().isForbidden());
		}
		pedir("", adminA).andExpect(status().isOk());
		pedir("", administrativoA).andExpect(status().isOk());
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
	}

	// ---- Cada número, con datos armados

	// Rango 2/11 a 15/11 de 2026: lunes a domingo de dos semanas. Todo en hora argentina.
	private void armarDatos() {
		// Bordes del rango: el primer instante (incluido), un segundo antes (fuera), el último segundo (incluido) y el
		// primer instante del día siguiente (fuera). En UTC el segundo reclamo caería dentro del rango.
		Reclamo r1 = reclamo("BACHE", "2026-11-02T00:00:00");
		reclamo("LUZ", "2026-11-01T23:59:59");
		Reclamo r3 = reclamo("BACHE", "2026-11-15T23:59:59");
		reclamo("AGUA", "2026-11-16T00:00:00");
		cambiar(r3, CambioDeEstado.asignar(agenteA.getId()));
		assertThat(r1.getEstado()).isEqualTo(EstadoReclamo.RECIBIDO);

		// Descartado: cuenta como creado en el período, pero no como sin asignar.
		Reclamo descartado = reclamo("BASURA", "2026-11-09T12:00:00");
		cambiar(descartado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));

		// Resuelto en el rango (creado el 10, resuelto el 12 a las 12:00: 60 horas).
		Reclamo r6 = reclamo("ARBOL", "2026-11-10T00:00:00");
		resolver(r6, "2026-11-12T12:00:00");

		// Creado ANTES del rango (20/10 10:00) y resuelto el 3/11 a las 10:00 (14 días: 336 horas). Cuenta como resuelto,
		// no como creado en el período.
		Reclamo r7 = reclamo("OTRO", "2026-10-20T10:00:00");
		resolver(r7, "2026-11-03T10:00:00");

		// Resuelto el 14 a las 12:00 (creado el 5 a las 12:00: 216 horas) y reabierto después: cuenta por haber pasado a
		// RESUELTO en el rango, aunque hoy esté en reparación.
		Reclamo r8 = reclamo("AGUA", "2026-11-05T12:00:00");
		resolver(r8, "2026-11-14T12:00:00");
		reabrir(r8);

		// Resuelto una primera vez antes del rango (1/11), reabierto y resuelto de nuevo el 6 a las 07:00 (creado el 4 a las
		// 00:00: 55 horas). Cuenta una sola vez, por la última.
		Reclamo r9 = reclamo("LUZ", "2026-11-04T00:00:00");
		resolver(r9, "2026-11-01T12:00:00");
		reabrir(r9);
		cambiar(r9, CambioDeEstado.resolver("De nuevo"));
		fecharUltimoCambio(r9, "2026-11-06T07:00:00");

		// Resuelto el 5 dentro del rango, reabierto y vuelto a resolver el 20 (fuera): la última vez cae afuera, no cuenta.
		Reclamo r10 = reclamo("OTRO", "2026-11-03T08:00:00");
		resolver(r10, "2026-11-05T08:00:00");
		reabrir(r10);
		cambiar(r10, CambioDeEstado.resolver("Otra vez"));
		fecharUltimoCambio(r10, "2026-11-20T08:00:00");

		// De otra comuna, en el rango y resuelto: no se cuenta para la A.
		Reclamo deB = reclamo(comunaB, "BACHE", ba("2026-11-04T10:00:00"));
		cambiar(deB, CambioDeEstado.asignar(usuario(comunaB, Rol.AGENTE).getId()));
	}

	@Test
	void cadaNumeroSaleDeLosDatosArmados() throws Exception {
		armarDatos();

		pedir("?desde=2026-11-02&hasta=2026-11-15").andExpect(status().isOk())
				.andExpect(jsonPath("$.desde").value("2026-11-02"))
				.andExpect(jsonPath("$.hasta").value("2026-11-15"))
				.andExpect(jsonPath("$.dias").value(14))
				// Creados en el rango: r1, r3, descartado, r6, r8, r9, r10 (no entran r2 ni r4 por un segundo, ni r7 por ser de antes).
				.andExpect(jsonPath("$.reclamosDelPeriodo").value(7))
				// Resueltos en el rango por su última vez: r6, r7, r8 y r9 (r10 se resolvió por última vez el 20).
				.andExpect(jsonPath("$.resueltos").value(4))
				// (60 + 336 + 216 + 55) / 4 = 166,75 -> 166,8
				.andExpect(jsonPath("$.tiempoPromedioResolucionHoras").value(166.8))
				// De los creados en el rango, solo r1 sigue en RECIBIDO (el descartado no).
				.andExpect(jsonPath("$.sinAsignar").value(1));
	}

	@Test
	void porTipoTrae_losSeisTiposEnOrdenConCeroDondeNoHubo() throws Exception {
		armarDatos();
		List<String> codigos = tipos.findAllByOrderByOrdenAsc().stream().map(t -> t.getCodigo()).toList();
		assertThat(codigos).hasSize(6);
		Map<String, Integer> esperado = Map.of("BACHE", 2, "BASURA", 1, "ARBOL", 1, "AGUA", 1, "LUZ", 1, "OTRO", 1);

		ResultActions r = pedir("?desde=2026-11-02&hasta=2026-11-15").andExpect(jsonPath("$.porTipo", hasSize(6)))
				.andExpect(jsonPath("$.porTipo[*].tipo", contains(codigos.toArray())));
		for (int i = 0; i < 6; i++) {
			r.andExpect(jsonPath("$.porTipo[" + i + "].cantidad").value(esperado.get(codigos.get(i))))
					.andExpect(jsonPath("$.porTipo[" + i + "].tipoNombre").exists());
		}
		// Un período sin nada: los seis, en 0.
		pedir("?desde=2025-01-01&hasta=2025-01-31").andExpect(jsonPath("$.porTipo", hasSize(6)))
				.andExpect(jsonPath("$.porTipo[*].cantidad", contains(0, 0, 0, 0, 0, 0)));
	}

	@Test
	void porSemanaVaDeLunesADomingoEnHoraArgentinaYElBordeDeSemanaNoSeMezcla() throws Exception {
		armarDatos();

		// Semana 1 (2 a 8): r1 (lunes 00:00), r10 (3/11), r9 (4/11), r8 (5/11) = 4. Semana 2 (9 a 15): descartado (9),
		// r6 (10) y r3 (domingo 15 a las 23:59:59) = 3.
		pedir("?desde=2026-11-02&hasta=2026-11-15").andExpect(jsonPath("$.porSemana", hasSize(2)))
				.andExpect(jsonPath("$.porSemana[0].desde").value("2026-11-02"))
				.andExpect(jsonPath("$.porSemana[0].hasta").value("2026-11-08"))
				.andExpect(jsonPath("$.porSemana[0].cantidad").value(4))
				.andExpect(jsonPath("$.porSemana[1].desde").value("2026-11-09"))
				.andExpect(jsonPath("$.porSemana[1].hasta").value("2026-11-15"))
				.andExpect(jsonPath("$.porSemana[1].cantidad").value(3));
	}

	@Test
	void laPrimeraYLaUltimaSemanaSeRecortanAlRangoYLasVaciasVanConCero() throws Exception {
		// Miércoles 4/11 a jueves 26/11: semanas 4-8, 9-15, 16-22 (vacía) y 23-26.
		reclamo("BACHE", "2026-11-04T00:00:00");
		reclamo("BACHE", "2026-11-08T23:59:59");
		reclamo("BACHE", "2026-11-23T00:00:00");
		reclamo("BACHE", "2026-11-26T23:59:59");
		reclamo("BACHE", "2026-11-27T00:00:00"); // fuera

		pedir("?desde=2026-11-04&hasta=2026-11-26").andExpect(status().isOk()).andExpect(jsonPath("$.dias").value(23))
				.andExpect(jsonPath("$.porSemana[*].desde", contains("2026-11-04", "2026-11-09", "2026-11-16", "2026-11-23")))
				.andExpect(jsonPath("$.porSemana[*].hasta", contains("2026-11-08", "2026-11-15", "2026-11-22", "2026-11-26")))
				.andExpect(jsonPath("$.porSemana[*].cantidad", contains(2, 0, 0, 2)))
				.andExpect(jsonPath("$.reclamosDelPeriodo").value(4));
	}

	@Test
	void unSoloDiaEsUnaSemanaDeUnDia() throws Exception {
		reclamo("LUZ", "2026-11-11T00:00:00");
		reclamo("LUZ", "2026-11-11T23:59:59");
		reclamo("LUZ", "2026-11-12T00:00:00");

		pedir("?desde=2026-11-11&hasta=2026-11-11").andExpect(jsonPath("$.dias").value(1))
				.andExpect(jsonPath("$.reclamosDelPeriodo").value(2))
				.andExpect(jsonPath("$.porSemana", hasSize(1)))
				.andExpect(jsonPath("$.porSemana[0].desde").value("2026-11-11"))
				.andExpect(jsonPath("$.porSemana[0].hasta").value("2026-11-11"))
				.andExpect(jsonPath("$.porSemana[0].cantidad").value(2));
	}

	@Test
	void sinResueltosElPromedioEsNull() throws Exception {
		reclamo("BACHE", "2026-11-10T10:00:00");

		pedir("?desde=2026-11-02&hasta=2026-11-15").andExpect(jsonPath("$.resueltos").value(0))
				.andExpect(jsonPath("$.tiempoPromedioResolucionHoras").value(nullValue()));
	}

	@Test
	void unaResolucionJustoEnLosBordesDelRangoEntraYUnSegundoAfueraNo() throws Exception {
		Reclamo dentroAlInicio = reclamo("BACHE", "2026-10-01T00:00:00");
		resolver(dentroAlInicio, "2026-11-02T00:00:00");
		Reclamo dentroAlFinal = reclamo("BACHE", "2026-10-01T00:00:00");
		resolver(dentroAlFinal, "2026-11-15T23:59:59");
		Reclamo antes = reclamo("BACHE", "2026-10-01T00:00:00");
		resolver(antes, "2026-11-01T23:59:59");
		Reclamo despues = reclamo("BACHE", "2026-10-01T00:00:00");
		resolver(despues, "2026-11-16T00:00:00");

		pedir("?desde=2026-11-02&hasta=2026-11-15").andExpect(jsonPath("$.resueltos").value(2))
				.andExpect(jsonPath("$.reclamosDelPeriodo").value(0));
	}

	@Test
	void sinFechasSonLosUltimos30DiasHastaHoyConHoyIncluido() throws Exception {
		// Hoy es el miércoles 25/11/2026: el rango es 27/10 a 25/11 (30 días contando los dos extremos).
		reclamo("BACHE", "2026-10-26T23:59:59"); // fuera
		reclamo("BACHE", "2026-10-27T00:00:00"); // primer instante
		reclamo("BACHE", "2026-11-25T23:59:59"); // hoy, último segundo
		reclamo("BACHE", "2026-11-26T00:00:00"); // mañana: fuera

		pedir("").andExpect(status().isOk())
				.andExpect(jsonPath("$.desde").value("2026-10-27"))
				.andExpect(jsonPath("$.hasta").value("2026-11-25"))
				.andExpect(jsonPath("$.dias").value(30))
				.andExpect(jsonPath("$.reclamosDelPeriodo").value(2))
				.andExpect(jsonPath("$.porSemana[*].desde", contains("2026-10-27", "2026-11-02", "2026-11-09", "2026-11-16", "2026-11-23")))
				.andExpect(jsonPath("$.porSemana[*].hasta", contains("2026-11-01", "2026-11-08", "2026-11-15", "2026-11-22", "2026-11-25")))
				.andExpect(jsonPath("$.porSemana[*].cantidad", contains(1, 0, 0, 0, 1)));
	}

	@Test
	void unaComunaNoVeLosNumerosDeOtra() throws Exception {
		armarDatos();

		// La comuna B tiene un solo reclamo (creado el 4/11, asignado).
		pedir("?desde=2026-11-02&hasta=2026-11-15", adminB).andExpect(jsonPath("$.reclamosDelPeriodo").value(1))
				.andExpect(jsonPath("$.resueltos").value(0)).andExpect(jsonPath("$.sinAsignar").value(0))
				.andExpect(jsonPath("$.tiempoPromedioResolucionHoras").value(nullValue()));
	}

	// ---- Errores de fechas

	@Test
	void unaSolaFechaOUnaFechaMalEscritaDa400Validacion() throws Exception {
		for (String consulta : List.of("?desde=2026-11-02", "?hasta=2026-11-02", "?desde=2026-13-45&hasta=2026-11-02",
				"?desde=ayer&hasta=hoy", "?desde=02/11/2026&hasta=15/11/2026")) {
			pedir(consulta).andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACION"));
		}
	}

	@Test
	void desdePosteriorAHastaDa400FechasInvalidas() throws Exception {
		pedir("?desde=2026-11-15&hasta=2026-11-14").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("FECHAS_INVALIDAS"));
	}

	@Test
	void masDe366DiasDa400RangoMuyLargo_yExactamente366Vale() throws Exception {
		// 31/12/2025 a 31/12/2026 son 366 días (2026 no es bisiesto: 365 días más el primero).
		pedir("?desde=2025-12-31&hasta=2026-12-31").andExpect(status().isOk()).andExpect(jsonPath("$.dias").value(366));
		pedir("?desde=2025-12-30&hasta=2026-12-31").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("RANGO_MUY_LARGO"));
	}
}
