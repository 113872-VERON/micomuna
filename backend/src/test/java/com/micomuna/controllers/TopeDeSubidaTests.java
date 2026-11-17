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
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Rol;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;

// El tope de spring.servlet.multipart lo aplica el servidor (Tomcat), no MockMvc: por eso esta prueba levanta
// el servidor en un puerto libre y sube el archivo por HTTP de verdad.
// No escribe en la base: el superadmin del token existe solo en memoria (el repositorio lo devuelve simulado)
// y la comuna de la ruta no existe.
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
	private static final String RUTA_LOGO = "/api/admin/comunas/999999999/logo";

	@Autowired
	private TestRestTemplate http;

	@Autowired
	private JwtService jwt;

	@MockitoSpyBean
	private UsuarioRepository usuarios;

	private String token;

	@BeforeEach
	void preparar() {
		Usuario superadmin = new Usuario();
		superadmin.setId(ID_SUPERADMIN);
		superadmin.setCorreo("root@mail.com");
		superadmin.setNombre("Superadmin");
		superadmin.setRol(Rol.SUPERADMIN);
		superadmin.setActivo(true);
		doReturn(Optional.of(superadmin)).when(usuarios).findConComunaPorId(ID_SUPERADMIN);
		token = jwt.generar(superadmin);
	}

	private ResponseEntity<JsonNode> subir(byte[] bytes) {
		HttpHeaders cabeceras = new HttpHeaders();
		cabeceras.setContentType(MediaType.MULTIPART_FORM_DATA);
		cabeceras.setBearerAuth(token);
		MultiValueMap<String, Object> partes = new LinkedMultiValueMap<>();
		partes.add("archivo", new ByteArrayResource(bytes) {
			@Override
			public String getFilename() {
				return "logo.png";
			}
		});
		return http.exchange(RUTA_LOGO, HttpMethod.PUT, new HttpEntity<>(partes, cabeceras), JsonNode.class);
	}

	// Control: la misma subida con un archivo chico pasa el tope y llega al servicio (que no encuentra la comuna).
	@Test
	void unArchivoChicoPasaElTopeYLlegaAlServicio() {
		ResponseEntity<JsonNode> respuesta = subir(ImagenesDePrueba.png(8, 8));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("NO_ENCONTRADO");
	}

	// Supera max-file-size (512 KB) sin llegar a max-request-size (600 KB).
	@Test
	void unArchivoQueSuperaElTopePorArchivoDa400ArchivoMuyGrande() {
		ResponseEntity<JsonNode> respuesta = subir(Arrays.copyOf(ImagenesDePrueba.png(8, 8), 550 * 1024));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("ARCHIVO_MUY_GRANDE");
		assertThat(respuesta.getBody().get("detail").asText()).isEqualTo("El archivo es demasiado grande.");
	}

	// Supera también max-request-size (600 KB).
	@Test
	void unaSubidaQueSuperaElTopeDelPedidoDa400ArchivoMuyGrande() {
		ResponseEntity<JsonNode> respuesta = subir(Arrays.copyOf(ImagenesDePrueba.png(8, 8), 700 * 1024));

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(respuesta.getBody().get("codigo").asText()).isEqualTo("ARCHIVO_MUY_GRANDE");
	}
}
