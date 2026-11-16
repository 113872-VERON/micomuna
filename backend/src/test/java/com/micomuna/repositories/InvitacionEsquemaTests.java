package com.micomuna.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.micomuna.entities.CodigoRecuperacion;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.enums.TipoCodigo;

// Cubre la migración V4: usuarios pendientes de invitación (sin clave) y el tipo de los códigos.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InvitacionEsquemaTests {

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private CodigoRecuperacionRepository codigos;

	@Autowired
	private JdbcTemplate jdbc;

	private Usuario pendiente(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		c = comunas.saveAndFlush(c);

		Usuario u = new Usuario();
		u.setComuna(c);
		u.setCorreo("ana@mail.com");
		u.setNombre("Ana");
		u.setRol(Rol.AGENTE);
		u.setClaveHash(null);
		return usuarios.saveAndFlush(u);
	}

	private CodigoRecuperacion codigo(Usuario u, String hash) {
		CodigoRecuperacion c = new CodigoRecuperacion();
		c.setUsuario(u);
		c.setCodigoHash(hash);
		c.setVenceEn(Instant.now().plus(60, ChronoUnit.MINUTES));
		return c;
	}

	@Test
	void permiteGuardarUnUsuarioPendienteSinClave() {
		Usuario u = pendiente("t8-pendiente");

		assertThat(usuarios.findById(u.getId()).orElseThrow().getClaveHash()).isNull();
	}

	@Test
	void yaNoExisteLaColumnaDebeCambiarClave() {
		Integer columnas = jdbc.queryForObject(
				"select count(*) from user_tab_columns where table_name = 'USUARIO' and column_name = 'DEBE_CAMBIAR_CLAVE'",
				Integer.class);

		assertThat(columnas).isZero();
	}

	@Test
	void elCodigoTomaRecuperacionPorDefecto() {
		Usuario u = pendiente("t8-recuperacion");

		CodigoRecuperacion guardado = codigos.saveAndFlush(codigo(u, "t8-hash-1"));

		assertThat(codigos.findByCodigoHash("t8-hash-1").orElseThrow().getTipo()).isEqualTo(TipoCodigo.RECUPERACION);
		assertThat(guardado.getTipo()).isEqualTo(TipoCodigo.RECUPERACION);
	}

	@Test
	void guardaUnCodigoDeInvitacion() {
		Usuario u = pendiente("t8-invitacion");
		CodigoRecuperacion c = codigo(u, "t8-hash-2");
		c.setTipo(TipoCodigo.INVITACION);

		codigos.saveAndFlush(c);

		assertThat(codigos.findByCodigoHash("t8-hash-2").orElseThrow().getTipo()).isEqualTo(TipoCodigo.INVITACION);
	}

	@Test
	void rechazaUnTipoDeCodigoInvalido() {
		Usuario u = pendiente("t8-invalido");
		codigos.saveAndFlush(codigo(u, "t8-hash-3"));

		assertThatThrownBy(() -> jdbc.update("update codigo_recuperacion set tipo = 'OTRO' where codigo_hash = 't8-hash-3'"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
