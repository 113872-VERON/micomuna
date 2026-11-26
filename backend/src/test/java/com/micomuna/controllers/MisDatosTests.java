package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// MC-95: cada persona ve y cambia su nombre desde "Mi cuenta", sea cual sea su rol.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MisDatosTests {

	private static final String RUTA = "/api/auth/mis-datos";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private JwtService jwt;

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private Usuario usuario(Comuna comuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(comuna);
		u.setCorreo(correo);
		u.setClaveHash("hash-de-prueba");
		u.setNombre("Ana");
		u.setRol(rol);
		u.setActivo(true);
		return usuarios.saveAndFlush(u);
	}

	private ResultActions ver(Usuario u) throws Exception {
		return mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + jwt.generar(u)));
	}

	private ResultActions cambiar(Usuario u, String cuerpo) throws Exception {
		return mockMvc.perform(put(RUTA)
				.header("Authorization", "Bearer " + jwt.generar(u))
				.contentType(MediaType.APPLICATION_JSON)
				.content(cuerpo));
	}

	@Test
	void leeSusDatos() throws Exception {
		Usuario u = usuario(comuna("mc95-lee"), "ana@mail.com", Rol.VECINO);

		ver(u).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(u.getId()))
				.andExpect(jsonPath("$.nombre").value("Ana"))
				.andExpect(jsonPath("$.correo").value("ana@mail.com"))
				.andExpect(jsonPath("$.rol").value("VECINO"))
				.andExpect(jsonPath("$.comunaSlug").value("mc95-lee"));
	}

	@Test
	void cambiaElNombreYLoGuardaRecortado() throws Exception {
		Usuario u = usuario(comuna("mc95-cambia"), "ana@mail.com", Rol.AGENTE);

		cambiar(u, "{\"nombre\":\"   Ana María  \"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana María"))
				.andExpect(jsonPath("$.comunaSlug").value("mc95-cambia"));

		assertThat(usuarios.findById(u.getId()).orElseThrow().getNombre()).isEqualTo("Ana María");
		ver(u).andExpect(jsonPath("$.nombre").value("Ana María"));
	}

	@Test
	void aceptaUnNombreDeExactamente100Caracteres() throws Exception {
		Usuario u = usuario(comuna("mc95-cien"), "ana@mail.com", Rol.VECINO);

		cambiar(u, "{\"nombre\":\"" + "a".repeat(100) + "\"}").andExpect(status().isOk());
	}

	@Test
	void rechazaUnNombreVacioOSoloEspacios() throws Exception {
		Usuario u = usuario(comuna("mc95-vacio"), "ana@mail.com", Rol.VECINO);

		for (String cuerpo : new String[] { "{\"nombre\":\"\"}", "{\"nombre\":\"   \"}", "{}", "{\"nombre\":null}" }) {
			cambiar(u, cuerpo).andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"))
					.andExpect(jsonPath("$.errores.nombre").exists());
		}
		assertThat(usuarios.findById(u.getId()).orElseThrow().getNombre()).isEqualTo("Ana");
	}

	@Test
	void rechazaUnNombreDe101Caracteres() throws Exception {
		Usuario u = usuario(comuna("mc95-largo"), "ana@mail.com", Rol.VECINO);

		cambiar(u, "{\"nombre\":\"" + "a".repeat(101) + "\"}").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION"))
				.andExpect(jsonPath("$.errores.nombre").exists());
		assertThat(usuarios.findById(u.getId()).orElseThrow().getNombre()).isEqualTo("Ana");
	}

	@Test
	void rechazaCaracteresDeControlYDeDireccionDeTexto() throws Exception {
		Usuario u = usuario(comuna("mc95-control"), "ana@mail.com", Rol.VECINO);

		// Se mandan como escapes JSON: RTL override, salto de línea, NUL (todos en el medio, porque el trim() se come los
		// de los extremos) y un espacio de ancho cero.
		for (String nombre : new String[] { "Ana\\u202Efdp", "Ana\\nMaria", "A\\u0000na", "An\\u200Ba" }) {
			cambiar(u, "{\"nombre\":\"" + nombre + "\"}").andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.codigo").value("VALIDACION"))
					.andExpect(jsonPath("$.errores.nombre").value("El nombre tiene caracteres no permitidos"));
		}
		assertThat(usuarios.findById(u.getId()).orElseThrow().getNombre()).isEqualTo("Ana");
	}

	@Test
	void aceptaNombresConTildesEnieApostrofesYGuiones() throws Exception {
		Usuario u = usuario(comuna("mc95-letras"), "ana@mail.com", Rol.VECINO);

		cambiar(u, "{\"nombre\":\"José María O'Neill-Ñúñez\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("José María O'Neill-Ñúñez"));
	}

	// El filtro JWT arma la sesión con los datos de la base, no con la comuna que diga el token: un token con el id
	// de una persona de la comuna A y la comuna B no le da acceso a nada de B. Se actúa siempre sobre la comuna real
	// de ese usuario y nada de la comuna B cambia. (El 401 por comuna que no coincide lo cubre CuentaServiceTests,
	// porque por HTTP esa discrepancia no llega al servicio.)
	@Test
	void unTokenConLaComunaDeOtraComunaNoTocaNadaDeEsaComuna() throws Exception {
		Comuna a = comuna("mc95-aisla-a");
		Comuna b = comuna("mc95-aisla-b");
		Usuario deA = usuario(a, "ana@mail.com", Rol.VECINO);
		Usuario deB = usuario(b, "beto@mail.com", Rol.VECINO);

		Usuario falso = new Usuario();
		falso.setId(deA.getId());
		falso.setComuna(b);
		falso.setRol(Rol.VECINO);
		String token = jwt.generar(falso);

		mockMvc.perform(put(RUTA).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Cambiado\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.comunaSlug").value("mc95-aisla-a"))
				.andExpect(jsonPath("$.correo").value("ana@mail.com"));
		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token))
				.andExpect(jsonPath("$.comunaSlug").value("mc95-aisla-a"));

		assertThat(usuarios.findById(deB.getId()).orElseThrow().getNombre()).isEqualTo("Ana");
	}

	@Test
	void sinTokenDa401() throws Exception {
		mockMvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Ana\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unUsuarioDesactivadoDa401() throws Exception {
		Usuario u = usuario(comuna("mc95-inactivo"), "ana@mail.com", Rol.VECINO);
		String token = jwt.generar(u);
		u.setActivo(false);
		usuarios.saveAndFlush(u);

		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
		mockMvc.perform(put(RUTA).header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Otro\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unUsuarioBorradoDa401() throws Exception {
		Usuario u = usuario(comuna("mc95-borrado"), "ana@mail.com", Rol.VECINO);
		String token = jwt.generar(u);
		usuarios.delete(u);
		usuarios.flush();

		mockMvc.perform(get(RUTA).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
	}

	@Test
	void elSuperadminSinComunaTambienVeYCambiaSuNombre() throws Exception {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(x -> x.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Usuario u = usuario(null, "root@mail.com", Rol.SUPERADMIN);

		ver(u).andExpect(status().isOk())
				.andExpect(jsonPath("$.rol").value("SUPERADMIN"))
				.andExpect(jsonPath("$.comunaSlug").doesNotExist());
		cambiar(u, "{\"nombre\":\" Root \"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Root"));
		assertThat(usuarios.findById(u.getId()).orElseThrow().getNombre()).isEqualTo("Root");
	}

	@Test
	void mandarCorreoOrolEnElCuerpoNoLosCambia() throws Exception {
		Comuna c = comuna("mc95-ignora");
		Usuario u = usuario(c, "ana@mail.com", Rol.VECINO);

		cambiar(u, "{\"nombre\":\"Ana B\",\"correo\":\"otro@mail.com\",\"rol\":\"ADMIN_COMUNA\",\"id\":999999,\"comunaSlug\":\"otra\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.nombre").value("Ana B"))
				.andExpect(jsonPath("$.correo").value("ana@mail.com"))
				.andExpect(jsonPath("$.rol").value("VECINO"))
				.andExpect(jsonPath("$.comunaSlug").value("mc95-ignora"));

		Usuario guardado = usuarios.findById(u.getId()).orElseThrow();
		assertThat(guardado.getCorreo()).isEqualTo("ana@mail.com");
		assertThat(guardado.getRol()).isEqualTo(Rol.VECINO);
		assertThat(guardado.getComuna().getId()).isEqualTo(c.getId());
	}

	@Test
	void cambiarElNombreNoTocaAOtroUsuarioDeLaComuna() throws Exception {
		Comuna c = comuna("mc95-otro");
		Usuario ana = usuario(c, "ana@mail.com", Rol.VECINO);
		Usuario otra = usuario(c, "otra@mail.com", Rol.VECINO);

		cambiar(ana, "{\"nombre\":\"Cambiada\"}").andExpect(status().isOk());

		assertThat(usuarios.findById(otra.getId()).orElseThrow().getNombre()).isEqualTo("Ana");
	}
}
