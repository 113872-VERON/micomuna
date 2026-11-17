package com.micomuna.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.ComunaLogo;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.ColorComuna;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTests {

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private ComunaLogoRepository logos;

	@Autowired
	private UsuarioRepository usuarios;

	private Comuna comuna(String nombre, Provincia provincia, String slug) {
		Comuna c = new Comuna();
		c.setNombre(nombre);
		c.setProvincia(provincia);
		c.setSlug(slug);
		c.setActiva(true);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash("hash");
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
	private void sinSuperadmin() {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
	}

	@Test
	void noPermiteDosUsuariosConElMismoCorreoEnUnaComuna() {
		Comuna c = comuna("Salsipuedes", Provincia.CORDOBA, "t2-salsipuedes-cordoba");
		usuario(c, "ana@mail.com", Rol.VECINO);

		assertThatThrownBy(() -> usuario(c, "ana@mail.com", Rol.VECINO))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void permiteElMismoCorreoEnDosComunas() {
		Comuna a = comuna("Salsipuedes", Provincia.CORDOBA, "t2-salsipuedes-cba");
		Comuna b = comuna("Salsipuedes", Provincia.NEUQUEN, "t2-salsipuedes-nqn");
		usuario(a, "ana@mail.com", Rol.VECINO);
		usuario(b, "ana@mail.com", Rol.VECINO);

		assertThat(usuarios.findByComunaIdAndCorreo(a.getId(), "ana@mail.com")).isPresent();
		assertThat(usuarios.findByComunaIdAndCorreo(b.getId(), "ana@mail.com")).isPresent();
	}

	@Test
	void noPermiteDosSuperadminsConElMismoCorreo() {
		sinSuperadmin();
		usuario(null, "root@mail.com", Rol.SUPERADMIN);

		assertThat(usuarios.findByComunaIsNullAndCorreo("root@mail.com")).isPresent();
		assertThat(usuarios.existsByRol(Rol.SUPERADMIN)).isTrue();
		assertThatThrownBy(() -> usuario(null, "root@mail.com", Rol.SUPERADMIN))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void noPermiteDosSuperadminsAunqueTenganDistintoCorreo() {
		sinSuperadmin();
		usuario(null, "t3-uno@mail.com", Rol.SUPERADMIN);

		assertThatThrownBy(() -> usuario(null, "t3-dos@mail.com", Rol.SUPERADMIN))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void laComunaGuardaColorPorDefectoDepartamentoYLogo() {
		Comuna c = new Comuna();
		c.setNombre("Salsipuedes");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t3-salsipuedes-colon");
		c.setDepartamento("Departamento Colón");
		comunas.saveAndFlush(c);
		assertThat(c.tieneLogo()).isFalse();

		// El logo se guarda aparte (ComunaLogo); la comuna solo sabe si tiene uno.
		assertThat(logos.guardar(c.getId(), new byte[] { 1, 2, 3 }, "image/png")).isEqualTo(1);

		Comuna leida = comunas.findBySlug("t3-salsipuedes-colon").orElseThrow();
		assertThat(leida.getColor()).isEqualTo(ColorComuna.CELESTE);
		assertThat(leida.getDepartamento()).isEqualTo("Departamento Colón");
		assertThat(leida.tieneLogo()).isTrue();
		ComunaLogo logo = logos.findById(c.getId()).orElseThrow();
		assertThat(logo.getLogo()).containsExactly(1, 2, 3);
		assertThat(logo.getLogoTipo()).isEqualTo("image/png");
	}

	@Test
	void guardarElLogoDeUnaComunaQueNoExisteNoCambiaNada() {
		assertThat(logos.guardar(-999L, new byte[] { 1 }, "image/png")).isZero();
	}

	@Test
	void noPermiteUnLogoSinTipoOConTipoNoPermitido() {
		Long id = comuna("A", Provincia.CORDOBA, "t3-sin-tipo").getId();

		assertThatThrownBy(() -> logos.guardar(id, new byte[] { 1 }, null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> logos.guardar(id, new byte[] { 1 }, "image/svg+xml"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void noPermiteDosComunasConElMismoSlug() {
		comuna("Villa Maria", Provincia.CORDOBA, "t2-villa-maria");

		assertThat(comunas.findBySlug("t2-villa-maria")).isPresent();
		assertThat(comunas.existsBySlug("t2-no-existe")).isFalse();
		assertThatThrownBy(() -> comuna("Otra", Provincia.CORDOBA, "t2-villa-maria"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
