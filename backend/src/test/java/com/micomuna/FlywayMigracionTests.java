package com.micomuna;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class FlywayMigracionTests {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void laPrimeraMigracionSeAplico() {
		String valor = jdbcTemplate.queryForObject(
				"SELECT valor FROM sistema_info WHERE clave = 'proyecto'", String.class);

		assertThat(valor).isEqualTo("MiComuna");
	}
}
