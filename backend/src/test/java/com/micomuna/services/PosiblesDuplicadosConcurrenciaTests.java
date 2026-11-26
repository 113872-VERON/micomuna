package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
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

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// MC-26: dos personas que marcan "es un reclamo nuevo" a la vez, de verdad contra Oracle. Con el reclamo bloqueado se hacen de
// a una: una lo marca y la otra recibe YA_MARCADO (o CONFLICTO si se le vence la espera). Nunca un error inesperado.
// Sin @Transactional en la clase: los datos se confirman y se borran a mano al final.
@SpringBootTest
class PosiblesDuplicadosConcurrenciaTests {

	private static final int SEGUNDOS_MAXIMOS = 30;

	@Autowired
	private PosiblesDuplicadosService servicio;
	@Autowired
	private ComunaRepository comunas;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private ReclamoRepository reclamos;
	@Autowired
	private TipoReclamoRepository tipos;
	@Autowired
	private JdbcTemplate jdbc;

	private ExecutorService hilos;
	private Comuna comuna;
	private Usuario admin;

	@BeforeEach
	void preparar() {
		hilos = Executors.newFixedThreadPool(2);
		Comuna c = new Comuna();
		c.setNombre("Comuna de concurrencia");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t26-conc-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		admin = usuario("admin", Rol.ADMIN_COMUNA);
		Usuario vecino = usuario("vecino", Rol.VECINO);
		reclamo(vecino, 1, "-31.420083");
		reclamo(vecino, 2, "-31.420183");
	}

	@AfterEach
	void limpiar() {
		hilos.shutdownNow();
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

	private void reclamo(Usuario vecino, int numero, String latitud) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Un pozo " + numero);
		r.setLatitud(new BigDecimal(latitud));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setEstado(EstadoReclamo.RECIBIDO);
		reclamos.saveAndFlush(r);
	}

	private String marcar() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
		try {
			servicio.marcarNoEsDuplicado(1, admin.getId());
			return "OK";
		} catch (RuntimeException e) {
			return e.getClass().getSimpleName();
		} finally {
			SecurityContextHolder.clearContext();
		}
	}

	@Test
	void dosMarcasALaVezSeHacenDeAUnaYLaOtraRecibeYaMarcadoOConflicto() throws Exception {
		Future<String> a = hilos.submit(this::marcar);
		Future<String> b = hilos.submit(this::marcar);
		List<String> resultados = List.of(a.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS), b.get(SEGUNDOS_MAXIMOS, TimeUnit.SECONDS));

		assertThat(resultados.stream().filter("OK"::equals).count()).isEqualTo(1);
		assertThat(resultados.stream().filter(r -> !r.equals("OK")).toList()).allMatch(r -> r.matches(
				"RevisionDeDuplicadosException|PessimisticLockingFailureException|CannotAcquireLockException"));
		assertThat(jdbc.queryForObject("select count(*) from reclamo where id_comuna = ? and no_duplicado_en is not null",
				Integer.class, comuna.getId())).isEqualTo(1);
	}
}
