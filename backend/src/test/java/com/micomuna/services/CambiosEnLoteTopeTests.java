package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.micomuna.dtos.CambioDeEstadoEnLoteRequest;
import com.micomuna.dtos.OmitidoDto;
import com.micomuna.dtos.ResultadoEnLoteDto;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.CodigoOmitido;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// El tope total de un lote (MC-18) con bloqueos reales de Oracle y tiempos reales, SIN @Transactional en la clase.
// Un hilo retiene el SELECT ... FOR UPDATE de tres reclamos; el lote (con el tope bajado a 5 s) pide el libre y los tres
// bloqueados. Cada bloqueado espera su WAIT 3 y falla con CONFLICTO; cuando el reloj pasa el tope, los que faltan salen
// "No se llegó a procesar" sin esperar nada más. Sin el tope serían 3 s por cada reclamo bloqueado.
// Orden determinista: [libre, bloqueado, bloqueado, bloqueado] ->
//   libre: se aplica enseguida; 1.º bloqueado: espera 3 s (t = 3); 2.º bloqueado: espera 3 s (t = 6, ya pasó el tope);
//   3.º bloqueado: empieza en t = 6 > 5, no se intenta.
@SpringBootTest
class CambiosEnLoteTopeTests {

	private static final Duration TOPE = Duration.ofSeconds(5);

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

	private ExecutorService hilo;
	private Comuna comuna;
	private Usuario admin;
	private Usuario agente;
	private Usuario vecino;

	@BeforeEach
	void preparar() {
		hilo = Executors.newSingleThreadExecutor();
		Comuna c = new Comuna();
		c.setNombre("Comuna de tope");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t18-tope-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		admin = usuario("admin", Rol.ADMIN_COMUNA);
		agente = usuario("agente", Rol.AGENTE);
		vecino = usuario("vecino", Rol.VECINO);
		for (int numero = 1; numero <= 4; numero++) {
			reclamo(numero);
		}
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
		lotes.topeTotal = TOPE;
	}

	@AfterEach
	void limpiar() {
		lotes.topeTotal = Duration.ofSeconds(30);
		hilo.shutdownNow();
		SecurityContextHolder.clearContext();
		Long id = comuna.getId();
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo where id_comuna = ?", id);
		jdbc.update("delete from usuario where id_comuna = ?", id);
		jdbc.update("delete from comuna where id = ?", id);
	}

	private Usuario usuario(String nombre, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(nombre + "-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com");
		u.setNombre(nombre);
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private void reclamo(int numero) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Un pozo");
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setEstado(EstadoReclamo.RECIBIDO);
		reclamos.saveAndFlush(r);
	}

	private String estadoDe(int numero) {
		return jdbc.queryForObject("select estado from reclamo where id_comuna = ? and numero = ?", String.class,
				comuna.getId(), numero);
	}

	private int filasDeHistorial(int numero) {
		return jdbc.queryForObject("select count(*) from reclamo_estado e join reclamo r on r.id = e.id_reclamo "
				+ "where r.id_comuna = ? and r.numero = ?", Integer.class, comuna.getId(), numero);
	}

	@Test
	void elTopeTotalCortaElLoteConBloqueosRealesSinEsperarTresSegundosPorCadaUno() throws Exception {
		CountDownLatch bloqueados = new CountDownLatch(1);
		CountDownLatch liberar = new CountDownLatch(1);
		// Este hilo toma (y retiene) el bloqueo de fila de los reclamos 2, 3 y 4.
		Future<Boolean> bloqueo = hilo.submit(() -> new TransactionTemplate(transacciones).execute(estado -> {
			for (int numero = 2; numero <= 4; numero++) {
				reclamos.bloquearPorNumero(comuna.getId(), numero).orElseThrow();
			}
			bloqueados.countDown();
			try {
				return liberar.await(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(e);
			}
		}));
		assertThat(bloqueados.await(10, TimeUnit.SECONDS)).as("el hilo tomó los bloqueos").isTrue();

		ResultadoEnLoteDto resultado;
		long inicio = System.nanoTime();
		try {
			resultado = lotes.cambiarEstado(
					new CambioDeEstadoEnLoteRequest(List.of(1, 2, 3, 4), EstadoReclamo.ASIGNADO, agente.getId()),
					admin.getId());
		} finally {
			liberar.countDown();
		}
		Duration duracion = Duration.ofNanos(System.nanoTime() - inicio);
		System.out.println("Tope del lote: duración medida = " + duracion.toMillis() + " ms (tope " + TOPE.toSeconds()
				+ " s, WAIT 3 s, sin tope serían unos 9 s)");
		assertThat(bloqueo.get(30, TimeUnit.SECONDS)).isTrue();

		// Tiempo acotado: pasó el tope (se esperaron dos WAIT 3 = ~6 s) y no llegó a los ~9 s de esperar por los tres.
		assertThat(duracion).isGreaterThanOrEqualTo(TOPE).isLessThan(Duration.ofSeconds(8));

		assertThat(resultado.aplicados()).containsExactly(1);
		List<OmitidoDto> omitidos = resultado.omitidos();
		assertThat(omitidos).extracting(OmitidoDto::numero).containsExactly(2, 3, 4);
		assertThat(omitidos).extracting(OmitidoDto::codigo).containsOnly(CodigoOmitido.CONFLICTO);
		// 2 y 3 se intentaron y vencieron por el bloqueo; el 4 ya no se intentó.
		assertThat(omitidos.get(0).motivo()).contains("lo está cambiando otra persona");
		assertThat(omitidos.get(1).motivo()).contains("lo está cambiando otra persona");
		assertThat(omitidos.get(2).motivo()).isEqualTo("No se llegó a procesar: probá de nuevo.");

		// Nada quedó a medias: el libre aplicado con su historial; los demás intactos y sin filas de historial.
		assertThat(estadoDe(1)).isEqualTo("ASIGNADO");
		assertThat(filasDeHistorial(1)).isEqualTo(1);
		for (int numero = 2; numero <= 4; numero++) {
			assertThat(estadoDe(numero)).as("n.º " + numero).isEqualTo("RECIBIDO");
			assertThat(filasDeHistorial(numero)).as("historial del n.º " + numero).isZero();
		}

		// Con el bloqueo ya liberado, reintentar los tres funciona: no quedó ningún bloqueo colgado.
		ResultadoEnLoteDto reintento = lotes.cambiarEstado(
				new CambioDeEstadoEnLoteRequest(List.of(2, 3, 4), EstadoReclamo.ASIGNADO, agente.getId()), admin.getId());
		assertThat(reintento.aplicados()).containsExactly(2, 3, 4);
		assertThat(reintento.omitidos()).isEmpty();
	}
}
