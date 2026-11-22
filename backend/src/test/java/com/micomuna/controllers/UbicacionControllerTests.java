package com.micomuna.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.entities.Comuna;
import com.micomuna.entities.Usuario;
import com.micomuna.enums.Provincia;
import com.micomuna.enums.Rol;
import com.micomuna.exceptions.LimiteSuperadoException;
import com.micomuna.repositories.ComunaRepository;
import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtService;
import com.micomuna.services.DireccionService;

// GET /api/ubicacion/direccion: roles, validación y respuestas. El servicio está simulado: no sale a internet.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UbicacionControllerTests {

	private static final String RUTA = "/api/ubicacion/direccion";

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private ComunaRepository comunas;
	@Autowired
	private UsuarioRepository usuarios;
	@Autowired
	private JwtService jwt;
	@MockitoBean
	private DireccionService direcciones;

	private Comuna comuna;

	@BeforeEach
	void preparar() {
		usuarios.deleteAll(usuarios.findAll().stream().filter(u -> u.getRol() == Rol.SUPERADMIN).toList());
		usuarios.flush();
		Comuna c = new Comuna();
		c.setNombre("Comuna de prueba");
		c.setProvincia(Provincia.CORDOBA);
		c.setSlug("t16-ubicacion");
		comuna = comunas.saveAndFlush(c);
	}

	private String token(Rol rol) {
		Usuario u = new Usuario();
		u.setComuna(rol == Rol.SUPERADMIN ? null : comuna);
		u.setCorreo("ubi16-" + rol.name().toLowerCase() + "@mail.com");
		u.setNombre("Nombre");
		u.setRol(rol);
		u.setClaveHash("hash");
		u.setActivo(true);
		return jwt.generar(usuarios.saveAndFlush(u));
	}

	private ResultActions pedir(String token, String latitud, String longitud) throws Exception {
		var pedido = get(RUTA);
		if (latitud != null) {
			pedido.param("latitud", latitud);
		}
		if (longitud != null) {
			pedido.param("longitud", longitud);
		}
		return mockMvc.perform(token == null ? pedido : pedido.header("Authorization", "Bearer " + token));
	}

	@ParameterizedTest
	@EnumSource(value = Rol.class, names = { "VECINO", "AGENTE", "ADMINISTRATIVO", "ADMIN_COMUNA" })
	void losRolesDeComunaRecibenLaDireccion(Rol rol) throws Exception {
		when(direcciones.direccionDe(anyLong(), eq(-31.42), eq(-64.19))).thenReturn(Optional.of("Avenida San Martín 120, Agua de Oro"));
		pedir(token(rol), "-31.42", "-64.19")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.direccion").value("Avenida San Martín 120, Agua de Oro"));
	}

	@Test
	void sinDireccionResponde204SinCuerpo() throws Exception {
		when(direcciones.direccionDe(any(), anyDouble(), anyDouble())).thenReturn(Optional.empty());
		pedir(token(Rol.VECINO), "-31.42", "-64.19").andExpect(status().isNoContent()).andExpect(content().string(""));
	}

	@Test
	void demasiadosPedidosResponde429ConSuCodigo() throws Exception {
		when(direcciones.direccionDe(any(), anyDouble(), anyDouble()))
				.thenThrow(new LimiteSuperadoException("DEMASIADOS_PEDIDOS", "Se están pidiendo muchas direcciones a la vez."));
		pedir(token(Rol.VECINO), "-31.42", "-64.19")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.codigo").value("DEMASIADOS_PEDIDOS"));
	}

	@Test
	void elSuperadminRecibe403AunqueLosParametrosSeanInvalidos() throws Exception {
		String token = token(Rol.SUPERADMIN);
		pedir(token, "-31.42", "-64.19").andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
		pedir(token, null, null).andExpect(status().isForbidden());
		pedir(token, "999", "abc").andExpect(status().isForbidden());
		verify(direcciones, never()).direccionDe(any(), anyDouble(), anyDouble());
	}

	@Test
	void sinSesionDa401() throws Exception {
		pedir(null, "-31.42", "-64.19").andExpect(status().isUnauthorized());
		pedir("token-que-no-es", "-31.42", "-64.19").andExpect(status().isUnauthorized());
	}

	@Test
	void latitudYLongitudSonObligatorias() throws Exception {
		String token = token(Rol.VECINO);
		pedir(token, null, "-64.19").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION")).andExpect(jsonPath("$.errores.latitud").exists());
		pedir(token, "-31.42", null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.codigo").value("VALIDACION")).andExpect(jsonPath("$.errores.longitud").exists());
		pedir(token, null, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.latitud").exists()).andExpect(jsonPath("$.errores.longitud").exists());
		verify(direcciones, never()).direccionDe(any(), anyDouble(), anyDouble());
	}

	@Test
	void latitudYLongitudTienenQueEstarEnRango() throws Exception {
		String token = token(Rol.VECINO);
		pedir(token, "90.000001", "-64.19").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.latitud").exists());
		pedir(token, "-90.5", "-64.19").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.latitud").exists());
		pedir(token, "-31.42", "180.5").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.longitud").exists());
		pedir(token, "-31.42", "-181").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.longitud").exists());
		pedir(token, "NaN", "-64.19").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.latitud").exists());
		pedir(token, "-31.42", "Infinity").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.longitud").exists());
		pedir(token, "abc", "-64.19").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores.latitud").value("El valor no es válido"));
		verify(direcciones, never()).direccionDe(any(), anyDouble(), anyDouble());
	}

	@Test
	void losExtremosDelRangoSonValidos() throws Exception {
		when(direcciones.direccionDe(any(), anyDouble(), anyDouble())).thenReturn(Optional.empty());
		pedir(token(Rol.VECINO), "-90", "180").andExpect(status().isNoContent());
		pedir(token(Rol.AGENTE), "90", "-180").andExpect(status().isNoContent());
	}
}
