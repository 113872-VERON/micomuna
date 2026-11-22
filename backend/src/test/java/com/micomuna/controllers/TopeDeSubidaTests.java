package com.micomuna.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.nio.channels.Selector;
import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// El tope de spring.servlet.multipart lo aplica el servidor (Tomcat), no MockMvc: por eso esta prueba levanta
// el servidor en un puerto libre y sube los archivos por HTTP de verdad.
// Topes: 2 MB por archivo (lo que pesa como mucho una foto de un reclamo) y 7 MB por pedido (3 fotos y los campos).
// No escribe en la base: los usuarios de los tokens existen solo en memoria (el repositorio los devuelve simulados),
// la comuna del logo tampoco existe en la base y el reclamo se rechaza antes de guardar nada.
//
// Se saltea (queda como "Skipped", no como error) si Java no puede abrir conexiones locales en esta máquina:
// sin eso no arranca ningún servidor. Pasa en Windows cuando la carpeta temporal del usuario no admite los
// sockets que usa Java; se arregla corriendo las pruebas con
// ./mvnw test "-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Windows/Temp"
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EnabledIf(value = "javaPuedeAbrirConexionesLocales", disabledReason = "Java no puede abrir conexiones locales en esta máquina (ver el comentario de la clase)")
class TopeDeSubidaTests {

	static boolean javaPuedeAbrirConexionesLocales() {
		try (Selector selector = Selector.open()) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static final long ID_SUPERADMIN = -1L;
	private static final long ID_VECINO = -2L;
	private static final long ID_COMUNA_DEL_VECINO = -3L;
	private static final long ID_COMUNA_DEL_LOGO = 999999999L;
	private static final String RUTA_LOGO = "/api/admin/comunas/" + ID_COMUNA_DEL_LOGO + "/logo";
	private static final int DOS_MB = 2 * 1024 * 1024;

	@Autowired
	private TestRestTemplate http;

	@Autowired
	private JwtService jwt;

	@MockitoSpyBean
	private UsuarioRepository usuarios;

	@MockitoSpyBean
	private ComunaRepository comunas;

	private String tokenSuperadmin;
	private String tokenVecino;

	@BeforeEach
	void preparar() {
		Usuario superadmin = new Usuario();
		superadmin.setId(ID_SUPERADMIN);
		superadmin.setCorreo("root@mail.com");
		superadmin.setNombre("Superadmin");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setActivo(true);
		doReturn(Optional.of(superadmin)).when(usuarios).findConComunaPorId(ID_SUPERADMIN);
		tokenSuperadmin = jwt.generar(superadmin);

		Comuna comuna = new Comuna();
		comuna.setId(ID_COMUNA_DEL_VECINO);
		comuna.setActiva(true);
		Usuario vecino = new Usuario();
		vecino.setId(ID_VECINO);
		vecino.setComuna(comuna);
		vecino.setCorreo("vecino@mail.com");
		vecino.setNombre("Vecino");
		vecino.setRol(Rol.VECINO);
		vecino.setActivo(true);
		doReturn(Optional.of(vecino)).when(usuarios).findConComunaPorId(ID_VECINO);
		tokenVecino = jwt.generar(vecino);
	}

	private static ByteArrayResource archivo(byte[] bytes, String nombre) {
		return new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return nombre;
			}
		};
	}

	private static HttpHeaders cabeceras(String token) {
		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);
		cabeceras.setBearerAuth(token);
		return cabeceras;
	}

	private ResponseEntity<JsonNode> subirLogo(byte[] bytes) {
		MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
		partes.add("archivo", archivo(bytes, "logo.png"));
		return http.exchange(RUTA_LOGO, HttpMethod.PUT, new HttpEntity<>(partes, cabeceras(tokenSuperadmin)),
				JsonNode.class);
	}

	// Un reclamo sin ubicación: si el pedido pasa el tope, el servicio lo rechaza antes de guardar nada.
	private ResponseEntity<JsonNode> reportarSinUbicacion(byte[]... fotos) {
		MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
		partes.add("tipo", "BACHE");
		partes.add("descripcion", "Pozo en la esquina");
		for (byte[] foto : fotos) {
			partes.add("fotos", archivo(foto, "foto.jpg"));
		}
		return http.exchange("/api/reclamos", HttpMethod.POST, new HttpEntity<>(partes, cabeceras(tokenVecino)),
				JsonNode.class);
	}

	// Control: la misma subida con un archivo chico pasa el tope y llega al servicio (que no encuentra la comuna).
	@Test
	void unArchivoChicoPasaElTopeYLlegaAlServicio() {
		ResponseEntity<JsonNode> respuesta = subirLogo(ImagenesDePrueba.png(8, 8));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("NO_ENCONTRADO");
	}

	// El tope del servidor subió a 2 MB por las fotos, pero el logo sigue limitado a 512 KB por ComunaService.
	@Test
	void unLogoDe600KBPasaElTopeDelServidorPeroSeRechazaComoLogoInvalido() {
		doReturn(true).when(comunas).existsById(ID_COMUNA_DEL_LOGO);

		ResponseEntity<JsonNode> respuesta = subirLogo(Arrays.copyOf(ImagenesDePrueba.png(8, 8), 600 * 1024));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("LOGO_INVALIDO");
	}

	// Supera max-file-size (2 MB) sin llegar a max-request-size (7 MB).
	@Test
	void unArchivoDeMasDe2MBDa400ArchivoMuyGrande() {
		ResponseEntity<JsonNode> respuesta = subirLogo(Arrays.copyOf(ImagenesDePrueba.png(8, 8), DOS_MB + 1));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("ARCHIVO_MUY_GRANDE");
		assertThat(respuesta.getBody().get("detail").asText()).isEqualTo("El archivo es demasiado grande.");
	}

	// Tres fotos de exactamente 2 MB, más los campos, entran en el tope y llegan al servicio.
	@Test
	void tresFotosDe2MBEntranEnElTopeDelPedido() {
		byte[] foto = Arrays.copyOf(ImagenesDePrueba.jpeg(8, 8), DOS_MB);

		ResponseEntity<JsonNode> respuesta = reportarSinUbicacion(foto, foto, foto);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("UBICACION_REQUERIDA");
	}

	// Cada archivo entra en los 2 MB, pero juntos superan max-request-size (7 MB).
	@Test
	void unPedidoDeMasDe7MBDa400ArchivoMuyGrande() {
		byte[] foto = Arrays.copyOf(ImagenesDePrueba.jpeg(8, 8), DOS_MB - 1024);

		ResponseEntity<JsonNode> respuesta = reportarSinUbicacion(foto, foto, foto, foto);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("ARCHIVO_MUY_GRANDE");
	}
}
