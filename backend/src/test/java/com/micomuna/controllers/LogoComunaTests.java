package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaLogoRepository;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LogoComunaTests {

	// Imágenes de verdad: el servicio las abre, no alcanza con que tengan la firma.
	private static final byte[] PNG = ImagenesDePrueba.png(16, 16);
	private static final byte[] JPEG = ImagenesDePrueba.jpeg(16, 16);
	private static final byte[] FIRMA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
	private static final byte[] FIRMA_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 };

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ComunaRepository comunas;

	@Autowired
	private ComunaLogoRepository logos;

	@Autowired
	private UsuarioRepository usuarios;

	@Autowired
	private PasswordEncoder encoder;

	@Autowired
	private JwtService jwt;

	@Autowired
	private EntityManager em;

	private Comuna comuna;
	private String tokenSuperadmin;

	@BeforeEach
	void preparar() {
		// Puede haber un superadmin real en la base de desarrollo; se borra dentro de la transacción de la prueba.
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		comuna = comuna("t12-logo");
		tokenSuperadmin = token(null, "root@mail.com", Rol.SUPERADMIN);
	}

	private Comuna comuna(String slug) {
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug(slug);
		return comunas.saveAndFlush(c);
	}

	private String token(Comuna deLaComuna, String correo, Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(deLaComuna);
		u.setCorreo(correo);
		u.setClaveHash(encoder.encode("clave-segura-1"));
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setActivo(true);
		return jwt.generar(usuarios.saveAndFlush(u));
	}

	private ResultActions subir(Long id, String token, String tipoDeclarado, byte[] bytes) throws Exception {
		var pedido = multipart(HttpMethod.PUT, "/api/admin/comunas/" + id + "/logo")
				.file(new MockMultipartFile("archivo", "logo", tipoDeclarado, bytes));
		return mockMvc.perform(token == null ? pedido : pedido.header("Authorization", "Bearer " + token));
	}

	private void esperarLogoInvalido(byte[] bytes) throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", bytes)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("LOGO_INVALIDO"));
	}

	// Se lee de la base y no de la sesión de Hibernate, para ver lo que quedó guardado de verdad.
	private boolean tieneLogoEnLaBase() {
		em.flush();
		em.clear();
		return comunas.findById(comuna.getId()).orElseThrow().tieneLogo();
	}

	@Test
	void elSuperadminSubeUnPngYSeSirveSinSesion() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneLogo").value(true))
				.andExpect(jsonPath("$.logo").doesNotExist());

		mockMvc.perform(get("/api/comunas/t12-logo/logo"))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(content().bytes(PNG));
		mockMvc.perform(get("/api/comunas/t12-logo")).andExpect(jsonPath("$.tieneLogo").value(true));
	}

	@Test
	void aceptaJpegYReemplazaElLogoAnterior() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG).andExpect(status().isOk());
		subir(comuna.getId(), tokenSuperadmin, "image/jpeg", JPEG).andExpect(status().isOk());

		mockMvc.perform(get("/api/comunas/t12-logo/logo"))
				.andExpect(header().string("Content-Type", "image/jpeg"))
				.andExpect(content().bytes(JPEG));
	}

	@Test
	void elTipoSeDecidePorElContenidoYNoPorLoQueDiceElCliente() throws Exception {
		// Un JPEG que se declara como PNG se guarda y se sirve como JPEG.
		subir(comuna.getId(), tokenSuperadmin, "image/png", JPEG).andExpect(status().isOk());

		mockMvc.perform(get("/api/comunas/t12-logo/logo")).andExpect(header().string("Content-Type", "image/jpeg"));
	}

	@Test
	void rechazaUnSvgAunqueSeDeclareComoPng() throws Exception {
		byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes();

		esperarLogoInvalido(svg);
		subir(comuna.getId(), tokenSuperadmin, "image/svg+xml", svg).andExpect(status().isBadRequest());
		assertThat(tieneLogoEnLaBase()).isFalse();
	}

	// ---- El archivo tiene que ser una imagen que se pueda abrir, no solo empezar como una

	@Test
	void rechazaUnArchivoConFirmaDePngYContenidoBasura() throws Exception {
		byte[] falso = Arrays.copyOf(FIRMA_PNG, 200);
		Arrays.fill(falso, FIRMA_PNG.length, falso.length, (byte) 0x41);

		esperarLogoInvalido(falso);
		esperarLogoInvalido(FIRMA_PNG);
		assertThat(tieneLogoEnLaBase()).isFalse();
	}

	@Test
	void rechazaUnArchivoConFirmaDeJpegYContenidoBasura() throws Exception {
		byte[] falso = Arrays.copyOf(FIRMA_JPEG, 200);
		Arrays.fill(falso, FIRMA_JPEG.length, falso.length, (byte) 0x41);

		esperarLogoInvalido(falso);
		assertThat(tieneLogoEnLaBase()).isFalse();
	}

	@Test
	void rechazaUnaImagenCortadaPorLaMitad() throws Exception {
		byte[] entera = ImagenesDePrueba.png(200, 200);

		esperarLogoInvalido(Arrays.copyOf(entera, entera.length / 2));
	}

	@Test
	void rechazaUnaImagenDeMasDe2000PixelesDeAnchoODeAlto() throws Exception {
		esperarLogoInvalido(ImagenesDePrueba.png(2001, 10));
		esperarLogoInvalido(ImagenesDePrueba.png(10, 2001));
		esperarLogoInvalido(ImagenesDePrueba.jpeg(2001, 10));
		assertThat(tieneLogoEnLaBase()).isFalse();
	}

	@Test
	void aceptaUnaImagenDeExactamente2000PixelesDeLado() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", ImagenesDePrueba.png(2000, 2000))
				.andExpect(status().isOk());
	}

	// ---- Peso del archivo

	@Test
	void rechazaUnArchivoVacio() throws Exception {
		esperarLogoInvalido(new byte[0]);
	}

	// Los bytes de relleno después del final de la imagen no impiden abrirla: sirven para llegar al peso exacto.
	@Test
	void rechazaUnLogoDeMasDe512KB() throws Exception {
		esperarLogoInvalido(Arrays.copyOf(PNG, 512 * 1024 + 1));
	}

	@Test
	void aceptaUnLogoDeExactamente512KB() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", Arrays.copyOf(PNG, 512 * 1024))
				.andExpect(status().isOk());
	}

	@Test
	void sinElArchivoDa400() throws Exception {
		mockMvc.perform(multipart(HttpMethod.PUT, "/api/admin/comunas/" + comuna.getId() + "/logo")
				.header("Authorization", "Bearer " + tokenSuperadmin))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("PETICION_INVALIDA"));
	}

	@Test
	void unaComunaQueNoExisteDa404() throws Exception {
		subir(999999L, tokenSuperadmin, "image/png", PNG).andExpect(status().isNotFound());
	}

	@Test
	void soloElSuperadminPuedeSubirElLogo() throws Exception {
		String vecino = token(comuna, "vecino@mail.com", Rol.VECINO);
		String admin = token(comuna, "jefe@mail.com", Rol.ADMIN_COMUNA);

		subir(comuna.getId(), null, "image/png", PNG).andExpect(status().isUnauthorized());
		subir(comuna.getId(), vecino, "image/png", PNG).andExpect(status().isForbidden());
		subir(comuna.getId(), admin, "image/png", PNG).andExpect(status().isForbidden());
	}

	@Test
	void sinLogoLaConsultaPublicaDa404() throws Exception {
		mockMvc.perform(get("/api/comunas/t12-logo/logo")).andExpect(status().isNotFound());
	}

	@Test
	void unaComunaInactivaNoSirveSuLogo() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG).andExpect(status().isOk());
		Comuna guardada = comunas.findById(comuna.getId()).orElseThrow();
		guardada.setActiva(false);
		comunas.saveAndFlush(guardada);

		mockMvc.perform(get("/api/comunas/t12-logo/logo")).andExpect(status().isNotFound());
	}

	@Test
	void unEnlaceInexistenteOInvalidoDa404() throws Exception {
		mockMvc.perform(get("/api/comunas/no-existe/logo")).andExpect(status().isNotFound());
		mockMvc.perform(get("/api/comunas/NO_VALIDO!/logo")).andExpect(status().isNotFound());
	}

	// ---- La imagen no viaja con la comuna

	// Cuida la regla del diseño: la entidad Comuna (que se carga en el login y en cada pedido con token)
	// no tiene ningún campo con los bytes de la imagen. Si alguien lo vuelve a agregar, esta prueba falla.
	@Test
	void laEntidadComunaNoCargaLosBytesDelLogo() {
		assertThat(em.getMetamodel().entity(Comuna.class).getAttributes())
				.noneMatch(atributo -> atributo.getJavaType() == byte[].class);
	}

	// Cambiar otros datos de la comuna no pisa el logo, aunque la Comuna en memoria no lo tenga cargado.
	@Test
	void cambiarOtrosDatosDeLaComunaNoBorraElLogo() throws Exception {
		subir(comuna.getId(), tokenSuperadmin, "image/png", PNG).andExpect(status().isOk());

		mockMvc.perform(patch("/api/admin/comunas/" + comuna.getId())
				.header("Authorization", "Bearer " + tokenSuperadmin)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nombre\":\"Otro nombre\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tieneLogo").value(true));

		assertThat(tieneLogoEnLaBase()).isTrue();
		assertThat(logos.findById(comuna.getId()).orElseThrow().getLogo()).isEqualTo(PNG);
	}
}
