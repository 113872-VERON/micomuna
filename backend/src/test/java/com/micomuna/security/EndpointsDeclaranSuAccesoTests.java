package com.micomuna.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Red de seguridad de MC-13: si alguien agrega un endpoint y se olvida de decidir quién puede usarlo, esta prueba falla.
// Cada endpoint de la aplicación tiene que estar en una de estas tres situaciones:
//  - ser público a propósito (lista PUBLICOS, la misma que SecurityConfig),
//  - caer bajo /api/admin/** (SecurityConfig lo restringe al superadmin) o bajo /api/auth/** (cualquier usuario con sesión),
//  - llevar @PreAuthorize (en el método o en la clase) con el rol permitido.
// Los endpoints de negocio por comuna (reclamos, usuarios) van siempre con @PreAuthorize.
@SpringBootTest
class EndpointsDeclaranSuAccesoTests {

	private static final Set<String> PUBLICOS = Set.of(
			"GET /api/comunas/{slug}",
			"POST /api/comunas/{slug}/registro",
			"POST /api/comunas/{slug}/login",
			"POST /api/comunas/{slug}/recuperar-clave",
			"POST /api/comunas/{slug}/restablecer-clave",
			"POST /api/comunas/{slug}/aceptar-invitacion",
			"POST /api/admin/login",
			"GET /api/salud");

	@Autowired
	private RequestMappingHandlerMapping mapeo;

	private static boolean esDeLaAplicacion(HandlerMethod metodo) {
		return metodo.getBeanType().getName().startsWith("com.micomuna.");
	}

	private static boolean esDeLasPruebas(HandlerMethod metodo) {
		// Los controladores de src/test (RutaProtegidaDePrueba y similares) no son parte de la API real.
		return metodo.getBeanType().getName().contains("DePrueba");
	}

	private static boolean tienePreAuthorize(HandlerMethod metodo) {
		return AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), PreAuthorize.class)
				|| AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), PreAuthorize.class);
	}

	@Test
	void todoEndpointDeclaraQuienPuedeUsarlo() {
		List<String> sinDeclarar = new ArrayList<>();
		int revisados = 0;
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeo.getHandlerMethods().entrySet()) {
			HandlerMethod metodo = entrada.getValue();
			if (!esDeLaAplicacion(metodo) || esDeLasPruebas(metodo)) {
				continue;
			}
			var patrones = entrada.getKey().getPathPatternsCondition().getPatternValues();
			var verbos = entrada.getKey().getMethodsCondition().getMethods();
			for (String patron : patrones) {
				for (Object verbo : verbos.isEmpty() ? Set.of("CUALQUIERA") : verbos) {
					revisados++;
					String endpoint = verbo + " " + patron;
					boolean publico = PUBLICOS.contains(endpoint);
					boolean reglaDeCadena = patron.startsWith("/api/admin/") || patron.startsWith("/api/auth/");
					if (!publico && !reglaDeCadena && !tienePreAuthorize(metodo)) {
						sinDeclarar.add(endpoint);
					}
				}
			}
		}
		assertThat(revisados).as("se revisó al menos un endpoint").isGreaterThan(5);
		assertThat(sinDeclarar).as("endpoints sin regla de acceso: agregales @PreAuthorize").isEmpty();
	}

	@Test
	void ningunEndpointPublicoSeAgregaSinQuererlo() {
		// Si un endpoint de PUBLICOS ya no existe, la lista quedó desactualizada y deja de proteger.
		List<String> existentes = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeo.getHandlerMethods().entrySet()) {
			for (String patron : entrada.getKey().getPathPatternsCondition().getPatternValues()) {
				for (Object verbo : entrada.getKey().getMethodsCondition().getMethods()) {
					existentes.add(verbo + " " + patron);
				}
			}
		}
		assertThat(existentes).containsAll(PUBLICOS);
	}
}
