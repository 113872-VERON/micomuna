package com.micomuna.configs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.UsuarioRepository;

@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class SuperadminInicializadorTests {

	private static final String CLAVE = "clave-del-superadmin-1";

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder encoder;

	@BeforeEach
	void sinSuperadmin() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
	}

	private void iniciar(String correo, String clave) throws Exception {
		new SuperadminInicializador(usuarios, encoder, correo, clave).run(null);
		usuarios.flush();
	}

	@Test
	void creaElSuperadminSiNoExiste() throws Exception {
		iniciar("root@mail.com", CLAVE);

		Usuario u = usuarios.findByComunaIsNullAndCorreo("root@mail.com").orElseThrow();
		assertThat(u.getRol()).isEqualTo(Rol.SUPERADMIN);
		assertThat(u.getComuna()).isNull();
		assertThat(u.isActivo()).isTrue();
		assertThat(u.getClaveHash()).isNotEqualTo(CLAVE);
		assertThat(encoder.matches(CLAVE, u.getClaveHash())).isTrue();
	}

	@Test
	void normalizaElCorreo() throws Exception {
		iniciar("  Root@Mail.com ", CLAVE);

		assertThat(usuarios.findByComunaIsNullAndCorreo("root@mail.com")).isPresent();
	}

	@Test
	void noCreaUnSegundoSuperadmin() throws Exception {
		iniciar("root@mail.com", CLAVE);

		iniciar("otro@mail.com", "otra-clave-del-admin-2");

		assertThat(usuarios.findByComunaIsNullAndCorreo("otro@mail.com")).isEmpty();
		assertThat(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN)).hasSize(1);
	}

	@Test
	void noPisaLaClaveDelSuperadminExistente() throws Exception {
		iniciar("root@mail.com", CLAVE);
		String hashOriginal = usuarios.findByComunaIsNullAndCorreo("root@mail.com").orElseThrow().getClaveHash();

		iniciar("root@mail.com", "una-clave-distinta-9");

		assertThat(usuarios.findByComunaIsNullAndCorreo("root@mail.com").orElseThrow().getClaveHash())
				.isEqualTo(hashOriginal);
	}

	@Test
	void noCreaNadaSinVariablesYAvisaEnElLog(CapturedOutput salida) throws Exception {
		iniciar("", "");
		iniciar(null, null);
		iniciar("root@mail.com", "");

		assertThat(usuarios.existsByRol(Rol.SUPERADMIN)).isFalse();
		assertThat(salida.getOut()).contains("SUPERADMIN_CORREO");
	}

	@Test
	void noCreaNadaConUnaClaveInvalidaYNoLaEscribeEnElLog(CapturedOutput salida) throws Exception {
		iniciar("root@mail.com", "corta");
		iniciar("root@mail.com", "ñ".repeat(40));

		assertThat(usuarios.existsByRol(Rol.SUPERADMIN)).isFalse();
		assertThat(salida.getOut()).doesNotContain("corta").doesNotContain("ñññ");
	}

	@Test
	void noCreaNadaConUnCorreoInvalido() throws Exception {
		iniciar("esto-no-es-un-correo", CLAVE);

		assertThat(usuarios.existsByRol(Rol.SUPERADMIN)).isFalse();
	}

	@Test
	void laClaveNuncaApareceEnElLog(CapturedOutput salida) throws Exception {
		iniciar("root@mail.com", CLAVE);

		assertThat(salida.getOut()).doesNotContain(CLAVE);
	}
}
