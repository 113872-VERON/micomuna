package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.micomuna.client.AsistenteIa;
import com.micomuna.client.AsistenteNinguno;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// MC-24: con la configuración por defecto (micomuna.ia.proveedor=ninguno) la IA está apagada: el alta anda igual y cada
// reclamo queda con una sugerencia OMITIDA. Sin @Transactional y sin asistente falso: es la aplicación tal cual arranca.
@SpringBootTest
@AutoConfigureMockMvc
class AltaSinIaTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AsistenteIa asistente;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	@Autowired
	private JdbcTemplate jdbc;

	private Comuna comuna;
	private Usuario vecino;

	@BeforeEach
	void preparar() {
		Comuna c = new Comuna();
		c.setNombre("Comuna sin IA");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t24n-" + UUID.randomUUID().toString().substring(0, 8));
		comuna = comunas.saveAndFlush(c);
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo("vecino-" + UUID.randomUUID().toString().substring(0, 6) + "@mail.com");
		u.setNombre("Vecino");
		u.setRol(Rol.VECINO);
		u.setClaveHash("hash");
		u.setActivo(true);
		vecino = usuarios.saveAndFlush(u);
	}

	@AfterEach
	void limpiar() throws Exception {
		Thread.sleep(300);
		Long id = comuna.getId();
		jdbc.update("delete from reclamo_sugerencia_ia where id_comuna = ?", id);
		jdbc.update("delete from reclamo_foto where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo_estado where id_reclamo in (select id from reclamo where id_comuna = ?)", id);
		jdbc.update("delete from reclamo where id_comuna = ?", id);
		jdbc.update("delete from usuario where id_comuna = ?", id);
		jdbc.update("delete from comuna where id = ?", id);
	}

	@Test
	void porDefectoElProveedorEsNingunoYCadaReclamoQuedaConUnaSugerenciaOmitida() throws Exception {
		assertThat(asistente).isInstanceOf(AsistenteNinguno.class);

		mockMvc.perform(multipart("/api/reclamos").param("tipo", "BACHE").param("descripcion", "Pozo grande")
				.param("latitud", "-31.42").param("longitud", "-64.18").header("Authorization", "Bearer " + jwt.generar(vecino)))
				.andExpect(status().isCreated());

		long limite = System.currentTimeMillis() + 5000;
		List<Map<String, Object>> filas = List.of();
		while (filas.isEmpty() && System.currentTimeMillis() < limite) {
			Thread.sleep(25);
			filas = jdbc.queryForList("select estado, proveedor, modelo, tipo_sugerido, tokens_entrada from reclamo_sugerencia_ia "
					+ "where id_comuna = ?", comuna.getId());
		}
		assertThat(filas).hasSize(1);
		assertThat(filas.get(0)).containsEntry("ESTADO", "OMITIDA").containsEntry("PROVEEDOR", "NINGUNO");
		assertThat(filas.get(0).get("MODELO")).isNull();
		assertThat(filas.get(0).get("TIPO_SUGERIDO")).isNull();
	}
}
