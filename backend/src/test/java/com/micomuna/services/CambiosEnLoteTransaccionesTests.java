package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.micomuna.dtos.CambioDeEstadoEnLoteRequest;
import com.micomuna.dtos.DescarteEnLoteRequest;
import com.micomuna.dtos.ResultadoEnLoteDto;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Reclamo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.CodigoOmitido;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.MotivoDescarte;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.ReclamoRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.UsuarioAutenticado;

// Los lotes (MC-18) con transacciones de verdad, SIN @Transactional en la clase: cada reclamo se confirma por
// separado, así que lo aplicado queda en la base aunque otro del mismo lote se omita. Limpia lo que crea.
@SpringBootTest
class CambiosEnLoteTransaccionesTests {

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
	private JdbcTemplate jdbc;

	private Comuna comuna;
	private Usuario admin;
	private Usuario agente;
	private Usuario vecino;

	@BeforeEach
	void preparar() {
		Comuna c = new Comuna();
		c.setNombre("Comuna de lotes");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t18-lote-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		admin = usuario("admin", Rol.ADMIN_COMUNA);
		agente = usuario("agente", Rol.AGENTE);
		vecino = usuario("vecino", Rol.VECINO);
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new UsuarioAutenticado(admin.getId(), Rol.ADMIN_COMUNA, comuna.getId()), null, List.of()));
	}

	@AfterEach
	void limpiar() {
		SecurityContextHolder.clearContext();
		Long id = comuna.getId();
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		// Los duplicados apuntan a su original: primero se sueltan.
		jdbc.update("update reclamo set id_reclamo_original = null where id_comuna = ?", id);
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

	private void reclamo(int numero, EstadoReclamo estado) {
		Reclamo r = new Reclamo();
		r.setComuna(comuna);
		r.setNumero(numero);
		r.setVecino(vecino);
		r.setTipo(tipos.findByCodigo("BACHE").orElseThrow());
		r.setDescripcion("Un pozo");
		r.setLatitud(new BigDecimal("-31.420083"));
		r.setLongitud(new BigDecimal("-64.188776"));
		r.setEstado(estado);
		if (estado == EstadoReclamo.DESCARTADO) {
			r.setMotivoDescarte(MotivoDescarte.OTRO);
		}
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
	void cadaReclamoDelLoteSeConfirmaPorSeparadoYElOmitidoNoFrenaALosDemas() {
		reclamo(1, EstadoReclamo.RECIBIDO);
		reclamo(2, EstadoReclamo.RESUELTO);
		reclamo(3, EstadoReclamo.RECIBIDO);

		ResultadoEnLoteDto resultado = lotes.cambiarEstado(
				new CambioDeEstadoEnLoteRequest(List.of(1, 2, 99, 3), EstadoReclamo.ASIGNADO, agente.getId()),
				admin.getId());

		assertThat(resultado.aplicados()).containsExactly(1, 3);
		assertThat(resultado.omitidos()).extracting(o -> o.numero(), o -> o.codigo())
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2, CodigoOmitido.TRANSICION_INVALIDA),
						org.assertj.core.groups.Tuple.tuple(99, CodigoOmitido.NO_ENCONTRADO));
		// Confirmado en la base (no solo en memoria): lo aplicado, aplicado; lo omitido, intacto.
		assertThat(estadoDe(1)).isEqualTo("ASIGNADO");
		assertThat(estadoDe(3)).isEqualTo("ASIGNADO");
		assertThat(estadoDe(2)).isEqualTo("RESUELTO");
		assertThat(filasDeHistorial(1)).isEqualTo(1);
		assertThat(filasDeHistorial(2)).isZero();
		assertThat(jdbc.queryForObject("select id_agente from reclamo where id_comuna = ? and numero = 1", Long.class,
				comuna.getId())).isEqualTo(agente.getId());
	}

	@Test
	void elLoteDeDescartesConfirmaLosDescartadosYDejaLosCerradosComoEstaban() {
		reclamo(1, EstadoReclamo.RECIBIDO);
		reclamo(2, EstadoReclamo.DESCARTADO);
		reclamo(3, EstadoReclamo.RECIBIDO);

		ResultadoEnLoteDto resultado = lotes.descartar(
				new DescarteEnLoteRequest(List.of(1, 2, 3), MotivoDescarte.NO_SE_PUEDE_ATENDER, "Fuera de alcance", null),
				admin.getId());

		assertThat(resultado.aplicados()).containsExactly(1, 3);
		assertThat(resultado.omitidos()).hasSize(1);
		assertThat(resultado.omitidos().get(0).codigo()).isEqualTo(CodigoOmitido.YA_CERRADO);
		assertThat(estadoDe(1)).isEqualTo("DESCARTADO");
		assertThat(estadoDe(3)).isEqualTo("DESCARTADO");
		assertThat(filasDeHistorial(2)).isZero();
	}
}
