package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.client.MailService;
import com.micomuna.dtos.CambioDeEstadoEnLoteRequest;
import com.micomuna.dtos.ResolverReclamoForm;
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
import com.micomuna.security.UsuarioAutenticado;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// MC-22 y MC-23: los correos se mandan de verdad después del commit y en otro hilo. A propósito SIN @Transactional en la
// clase (los cambios se confirman y los datos se borran a mano al final) y con un MailService falso que guarda lo enviado:
// con una transacción de prueba que se deshace nunca habría un commit y nunca se mandaría nada.
@SpringBootTest
class AvisosDeCambioDeEstadoTests {

	record Enviado(String destino, String asunto, String texto, String hilo) {
	}

	static class CorreoGrabador implements MailService {
		final List<Enviado> enviados = new CopyOnWriteArrayList<>();
		volatile boolean falla;

		@Override
		public void enviar(String destino, String asunto, String texto) {
			if (falla) {
				throw new IllegalStateException("SMTP caído");
			}
			enviados.add(new Enviado(destino, asunto, texto, Thread.currentThread().getName()));
		}
	}

	@TestConfiguration
	static class Correos {
		@Bean
		@Primary
		CorreoGrabador correoGrabador() {
			return new CorreoGrabador();
		}
	}

	@Autowired
	private CorreoGrabador correo;

	@Autowired
	private CambioDeEstadoService servicio;

	@Autowired
	private AgenteReclamosService agenteReclamos;

	@Autowired
	private CambiosEnLoteService lotes;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private ReclamoRepository reclamos;

	@Autowired
	private TipoReclamoRepository tipos;

	@Autowired
	private PlatformTransactionManager transacciones;

	@Autowired
	private JdbcTemplate jdbc;

	private Comuna comuna;
	private Usuario ana;
	private Usuario bea;
	private Usuario caro;
	private Usuario admin;
	private Usuario agente;
	private int contadorReclamos;
	private ListAppender<ILoggingEvent> registro;
	private Logger logger;

	@BeforeEach
	void preparar() {
		correo.enviados.clear();
		correo.falla = false;
		Comuna c = new Comuna();
		c.setNombre("Salsipuedes");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t22-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		ana = usuario("Ana", Rol.VECINO);
		bea = usuario("Bea", Rol.VECINO);
		caro = usuario("Caro", Rol.VECINO);
		admin = usuario("Admin", Rol.ADMIN_COMUNA);
		agente = usuario("Agente", Rol.AGENTE);
		logger = (Logger) LoggerFactory.getLogger(AvisosDeReclamoService.class);
		registro = new ListAppender<>();
		registro.start();
		logger.addAppender(registro);
	}

	@AfterEach
	void limpiar() throws Exception {
		logger.detachAppender(registro);
		// Deja terminar los envíos en vuelo antes de borrar nada.
		Thread.sleep(300);
		Long id = comuna.getId();
		jdbc.update("update reclamo set id_reclamo_original = null where id_comuna = ?", id);
		jdbc.update("delete from reclamo_foto where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo where id_comuna = ?", id);
		jdbc.update("delete from usuario where id_comuna = ?", id);
		jdbc.update("delete from comuna where id = ?", id);
	}

	// ---- Ayudas

	private Usuario usuario(String nombre, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(nombre.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Un reclamo ya creado y confirmado, con su alta en el historial (esa alta no manda correo).
	private Reclamo reclamo(Usuario vecino) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(++contadorReclamos);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Reclamo " + contadorReclamos);
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		reclamos.saveAndFlush(r);
		new TransactionTemplate(transacciones).executeWithoutResult(estado -> servicio.registrarAlta(r, vecino));
		return r;
	}

	private <T> T como(Usuario quien, Supplier<T> accion) {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(quien.getId(), quien.getRol(), comuna.getId()), null, List.of()));
		try {
			return accion.get();
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	private void cambiar(Reclamo r, CambioDeEstado cambio) {
		como(admin, () -> servicio.cambiar(r.getNumero(), cambio, admin.getId()));
	}

	// Espera (como mucho 5 s) a que lleguen `cantidad` correos: se mandan en otro hilo.
	private void esperar(int cantidad) throws Exception {
		long limite = System.currentTimeMillis() + 5000;
		while (correo.enviados.size() < cantidad && System.currentTimeMillis() < limite) {
			Thread.sleep(25);
		}
		assertThat(correo.enviados).hasSize(cantidad);
	}

	// Para comprobar que NO se manda nada: da tiempo de sobra al hilo de correo y mira que siga vacío.
	private void esperarQueNoLleguenMas(int cantidad) throws Exception {
		Thread.sleep(700);
		assertThat(correo.enviados).hasSize(cantidad);
	}

	private String estadoEnBase(Reclamo r) {
		return jdbc.queryForObject("select estado from reclamo where id = ?", String.class, r.getId());
	}

	// ---- Un correo por cada cambio de estado

	@Test
	void cadaCambioDeEstadoMandaUnCorreoAlVecinoEnOtroHiloYElAltaNo() throws Exception {
		Reclamo r = reclamo(ana);
		// El alta (RECIBIDO inicial) no avisa.
		esperarQueNoLleguenMas(0);

		cambiar(r, CambioDeEstado.asignar(agente.getId()));
		esperar(1);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		esperar(2);
		cambiar(r, CambioDeEstado.resolver("Se tapó el pozo"));
		esperar(3);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		esperar(4);

		assertThat(correo.enviados).allSatisfy(e -> {
			assertThat(e.destino()).isEqualTo(ana.getCorreo());
			assertThat(e.hilo()).startsWith("correo-");
			// Sin nombres del personal ni del agente.
			assertThat(e.texto()).doesNotContain("Admin").doesNotContain("Agente");
		});
		assertThat(correo.enviados.stream().map(Enviado::asunto).toList()).containsExactly("Tu reclamo n.º 1: Asignado",
				"Tu reclamo n.º 1: En reparación", "Tu reclamo n.º 1 fue resuelto", "Tu reclamo n.º 1: En reparación");
		assertThat(correo.enviados.get(2).texto()).contains("Lo que se hizo: Se tapó el pozo");
		assertThat(correo.enviados.get(0).texto()).contains("/" + comuna.getSlug() + "/reclamos/1");
	}

	@Test
	void unDescarteYSuReaperturaTambienAvisan() throws Exception {
		Reclamo r = reclamo(ana);

		cambiar(r, CambioDeEstado.descartar(MotivoDescarte.NO_ES_DE_LA_COMUNA, "Es de la ruta", null));
		esperar(1);
		cambiar(r, CambioDeEstado.a(EstadoReclamo.RECIBIDO));
		esperar(2);

		assertThat(correo.enviados.get(0).asunto()).isEqualTo("Tu reclamo n.º 1 fue descartado");
		assertThat(correo.enviados.get(0).texto()).contains("Motivo: No es un problema de esta comuna")
				.contains("Lo que nos dijo la comuna: Es de la ruta");
		assertThat(correo.enviados.get(1).asunto()).isEqualTo("Tu reclamo n.º 1: Recibido");
	}

	@Test
	void asignarDesdeRecibidoAvisa_laReasignacionNo_yDesdeEnReparacionVuelveAAvisar() throws Exception {
		Reclamo r = reclamo(ana);

		como(admin, () -> servicio.asignarAgente(1, agente.getId(), admin.getId()));
		esperar(1);
		assertThat(correo.enviados.get(0).asunto()).isEqualTo("Tu reclamo n.º 1: Asignado");

		// Reasignar a otro agente: el estado sigue igual, no hay correo.
		Usuario otro = usuario("Otro", Rol.AGENTE);
		como(admin, () -> servicio.asignarAgente(1, otro.getId(), admin.getId()));
		esperarQueNoLleguenMas(1);

		// En reparación -> asignado a otro agente: el estado cambia, sí avisa.
		cambiar(r, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		esperar(2);
		como(admin, () -> servicio.asignarAgente(1, agente.getId(), admin.getId()));
		esperar(3);
		assertThat(correo.enviados.get(2).asunto()).isEqualTo("Tu reclamo n.º 1: Asignado");
	}

	@Test
	void elAgenteQueEmpiezaYResuelveTambienDisparaLosCorreos() throws Exception {
		Reclamo r = reclamo(ana);
		cambiar(r, CambioDeEstado.asignar(agente.getId()));
		esperar(1);

		como(agente, () -> agenteReclamos.empezar(1, new UsuarioAutenticado(agente.getId(), Rol.AGENTE, comuna.getId())));
		esperar(2);
		como(agente, () -> agenteReclamos.resolver(1, new ResolverReclamoForm("Quedó listo", (MockMultipartFile) null),
				new UsuarioAutenticado(agente.getId(), Rol.AGENTE, comuna.getId())));
		esperar(3);

		assertThat(correo.enviados.get(1).asunto()).isEqualTo("Tu reclamo n.º 1: En reparación");
		assertThat(correo.enviados.get(2).asunto()).isEqualTo("Tu reclamo n.º 1 fue resuelto");
		assertThat(correo.enviados.get(2).texto()).contains("Lo que se hizo: Quedó listo");
	}

	@Test
	void losCambiosEnLoteMandanUnCorreoPorCadaReclamoAplicado() throws Exception {
		reclamo(ana);
		reclamo(bea);
		Reclamo yaAsignado = reclamo(caro);
		cambiar(yaAsignado, CambioDeEstado.asignar(agente.getId()));
		esperar(1);
		correo.enviados.clear();

		como(admin, () -> lotes.cambiarEstado(new CambioDeEstadoEnLoteRequest(List.of(1, 2, 3), EstadoReclamo.ASIGNADO, agente.getId()),
				admin.getId()));

		// El tercero ya estaba asignado: se omite y no avisa.
		esperar(2);
		esperarQueNoLleguenMas(2);
		assertThat(correo.enviados.stream().map(Enviado::destino).toList())
				.containsExactlyInAnyOrder(ana.getCorreo(), bea.getCorreo());
	}

	// ---- Fallos y transacciones

	@Test
	void siElEnvioFallaElCambioQuedaYSeRegistraConNumeroYComunaSinElCorreo() throws Exception {
		Reclamo r = reclamo(ana);
		correo.falla = true;

		cambiar(r, CambioDeEstado.asignar(agente.getId()));

		long limite = System.currentTimeMillis() + 5000;
		while (registro.list.isEmpty() && System.currentTimeMillis() < limite) {
			Thread.sleep(25);
		}
		assertThat(estadoEnBase(r)).isEqualTo("ASIGNADO");
		assertThat(correo.enviados).isEmpty();
		assertThat(registro.list).hasSize(1);
		String mensaje = registro.list.get(0).getFormattedMessage();
		assertThat(mensaje).contains("n.º 1").contains(comuna.getSlug()).doesNotContain("@").doesNotContain("Ana");
	}

	@Test
	void siLaTransaccionSeDeshaceNoSeManda() throws Exception {
		Reclamo r = reclamo(ana);

		try {
			como(admin, () -> new TransactionTemplate(transacciones).execute(estado -> {
				servicio.cambiar(1, CambioDeEstado.asignar(agente.getId()), admin.getId());
				throw new IllegalStateException("algo falló después del cambio");
			}));
		} catch (IllegalStateException esperado) {
			assertThat(esperado).hasMessageContaining("algo falló");
		}

		esperarQueNoLleguenMas(0);
		assertThat(estadoEnBase(r)).isEqualTo("RECIBIDO");
	}

	@Test
	void elCorreoSaleDespuesDelCommit_noAntes() throws Exception {
		Reclamo r = reclamo(ana);

		como(admin, () -> new TransactionTemplate(transacciones).execute(estado -> {
			servicio.cambiar(1, CambioDeEstado.asignar(agente.getId()), admin.getId());
			try {
				// Con la transacción todavía abierta no salió nada.
				Thread.sleep(500);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			assertThat(correo.enviados).isEmpty();
			return null;
		}));

		esperar(1);
		assertThat(estadoEnBase(r)).isEqualTo("ASIGNADO");
	}

	// ---- Avisos a los vecinos de duplicados

	@Test
	void cuandoElOriginalCambiaSeAvisaALosVecinosDeSusDuplicadosSinRepetirYSinDatosDelOriginal() throws Exception {
		Reclamo original = reclamo(ana); // n.º 1
		Reclamo deBea1 = reclamo(bea); // n.º 2
		Reclamo deBea2 = reclamo(bea); // n.º 3
		Reclamo deCaro = reclamo(caro); // n.º 4
		Reclamo deAna = reclamo(ana); // n.º 5: la misma vecina que el original
		Reclamo noEsDuplicado = reclamo(caro); // n.º 6
		for (Reclamo d : List.of(deBea1, deBea2, deCaro, deAna)) {
			cambiar(d, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()));
		}
		cambiar(noEsDuplicado, CambioDeEstado.descartar(MotivoDescarte.OTRO, "No corresponde", null));
		esperar(5); // los cinco descartes avisaron a su propio vecino
		correo.enviados.clear();

		cambiar(original, CambioDeEstado.asignar(agente.getId()));

		// Ana (dueña del original y de un duplicado): un solo correo, el suyo. Bea: uno con sus dos reclamos. Caro: uno.
		esperar(3);
		esperarQueNoLleguenMas(3);
		assertThat(correo.enviados.stream().map(Enviado::destino).toList())
				.containsExactlyInAnyOrder(ana.getCorreo(), bea.getCorreo(), caro.getCorreo());
		Enviado aBea = correo.enviados.stream().filter(e -> e.destino().equals(bea.getCorreo())).findFirst().orElseThrow();
		assertThat(aBea.asunto()).isEqualTo("Novedades de tus reclamos n.º 2 y 3");
		assertThat(aBea.texto()).contains("La comuna ya está atendiendo los problemas que reportaste en los reclamos n.º 2 y 3. Hay novedades: ahora está «asignado».").doesNotContain("/reclamos/1").doesNotContain("n.º 1 ")
				.doesNotContain("Ana");
		Enviado aCaro = correo.enviados.stream().filter(e -> e.destino().equals(caro.getCorreo())).findFirst().orElseThrow();
		assertThat(aCaro.asunto()).isEqualTo("Novedades de tu reclamo n.º 4");
		Enviado aAna = correo.enviados.stream().filter(e -> e.destino().equals(ana.getCorreo())).findFirst().orElseThrow();
		assertThat(aAna.asunto()).isEqualTo("Tu reclamo n.º 1: Asignado");
	}

	@Test
	void unVecinoInactivoNoRecibeElAvisoDeSusDuplicados() throws Exception {
		Reclamo original = reclamo(ana); // n.º 1
		Reclamo deBea = reclamo(bea); // n.º 2
		Reclamo deCaro = reclamo(caro); // n.º 3
		cambiar(deBea, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()));
		cambiar(deCaro, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()));
		esperar(2);
		correo.enviados.clear();
		jdbc.update("update usuario set activo = 0 where id = ?", bea.getId());

		cambiar(original, CambioDeEstado.asignar(agente.getId()));

		esperar(2);
		esperarQueNoLleguenMas(2);
		assertThat(correo.enviados.stream().map(Enviado::destino).toList())
				.containsExactlyInAnyOrder(ana.getCorreo(), caro.getCorreo());
	}

	@Test
	void siElOriginalSeDescartaLosVecinosDeSusDuplicadosRecibenUnAvisoPropioConLosDatosDeContactoDeLaComuna() throws Exception {
		jdbc.update("update comuna set telefono = '3511234567', whatsapp = '5493511234567' where id = ?", comuna.getId());
		Reclamo original = reclamo(ana); // n.º 1
		Reclamo deBea = reclamo(bea); // n.º 2
		cambiar(deBea, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()));
		esperar(1);
		correo.enviados.clear();

		cambiar(original, CambioDeEstado.descartar(MotivoDescarte.NO_SE_PUEDE_ATENDER, "Obra mayor", null));

		esperar(2);
		Enviado aBea = correo.enviados.stream().filter(e -> e.destino().equals(bea.getCorreo())).findFirst().orElseThrow();
		assertThat(aBea.asunto()).isEqualTo("La comuna descartó el reclamo que seguía tu reclamo n.º 2");
		assertThat(aBea.texto()).contains("La comuna descartó el reclamo que seguía el problema que reportaste en el reclamo n.º 2. "
				+ "Si el problema sigue, comunicate con la comuna.").contains("Teléfono: 3511234567")
				.contains("WhatsApp: 5493511234567").doesNotContain("Obra mayor").doesNotContain("n.º 1.")
				.doesNotContain("/reclamos/1\n");
		Enviado aAna = correo.enviados.stream().filter(e -> e.destino().equals(ana.getCorreo())).findFirst().orElseThrow();
		assertThat(aAna.texto()).contains("Motivo: No se puede atender").contains("Lo que nos dijo la comuna: Obra mayor");
	}

	@Test
	void siElOriginalSeResuelveLosVecinosDeSusDuplicadosRecibenElCierre() throws Exception {
		Reclamo original = reclamo(ana); // n.º 1
		Reclamo deBea = reclamo(bea); // n.º 2
		cambiar(deBea, CambioDeEstado.descartar(MotivoDescarte.DUPLICADO, null, original.getNumero()));
		cambiar(original, CambioDeEstado.asignar(agente.getId()));
		cambiar(original, CambioDeEstado.a(EstadoReclamo.EN_REPARACION));
		esperar(5);
		correo.enviados.clear();

		cambiar(original, CambioDeEstado.resolver("Se tapó el pozo"));

		esperar(2);
		Enviado aBea = correo.enviados.stream().filter(e -> e.destino().equals(bea.getCorreo())).findFirst().orElseThrow();
		assertThat(aBea.asunto()).isEqualTo("Se resolvió el problema que reportaste (reclamo n.º 2)");
		assertThat(aBea.texto()).contains("La comuna resolvió el problema que reportaste en el reclamo n.º 2.")
				.contains("Lo que se hizo: Se tapó el pozo").doesNotContain("/reclamos/1\n").doesNotContain("Ana");
	}

	@Test
	void laFechaDelCorreoEsLaDeLaFilaDelHistorialDelCambio() throws Exception {
		Reclamo r = reclamo(ana);

		cambiar(r, CambioDeEstado.asignar(agente.getId()));
		esperar(1);

		java.sql.Timestamp guardada = jdbc.queryForObject(
				"select cambiado_en from reclamo_estado where id_reclamo = ? and estado = 'ASIGNADO'", java.sql.Timestamp.class,
				r.getId());
		// La base guarda hora UTC (hibernate.jdbc.time_zone=UTC): se lee tal cual y se muestra en hora argentina.
		String esperada = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").format(guardada.toLocalDateTime()
				.atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(com.micomuna.constants.Fechas.ARGENTINA));
		assertThat(correo.enviados.get(0).texto()).contains("Fecha: " + esperada + " (hora de Argentina)");
	}
}
