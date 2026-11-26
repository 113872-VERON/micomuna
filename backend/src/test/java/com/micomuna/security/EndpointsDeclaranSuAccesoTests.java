package com.micomuna.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

// Red de seguridad de MC-13: si alguien agrega un endpoint y se olvida de decidir quién puede usarlo, esta prueba falla.
// Cada endpoint de la aplicación tiene que estar en una de estas situaciones:
//  - ser público a propósito (lista PUBLICOS, la misma que SecurityConfig),
//  - ser de cualquier usuario con sesión, sea cual sea su rol, a propósito (lista CON_SESION),
//  - caer bajo /api/admin/** (SecurityConfig lo restringe al superadmin),
//  - llevar @PreAuthorize (en el método o en la clase) con una expresión que nombre el rol permitido.
// Los endpoints de negocio por comuna (reclamos, usuarios) van siempre con @PreAuthorize.
@SpringBootTest
class EndpointsDeclaranSuAccesoTests {

	private static final Set<String> PUBLICOS = Set.of(
			"GET /api/comunas/{slug}",
			"GET /api/comunas/{slug}/logo",
			"POST /api/comunas/{slug}/registro",
			"POST /api/comunas/{slug}/login",
			"POST /api/comunas/{slug}/recuperar-clave",
			"POST /api/comunas/{slug}/restablecer-clave",
			"POST /api/comunas/{slug}/aceptar-invitacion",
			"POST /api/admin/login",
			"GET /api/salud");

	// Endpoints que cualquier usuario con sesión puede usar, sin importar el rol (los frena el anyRequest().authenticated()
	// de SecurityConfig). Estar bajo /api/auth/ no alcanza: cada uno se agrega acá a propósito.
	private static final Set<String> CON_SESION = Set.of(
			"POST /api/auth/cambiar-clave",
			"GET /api/auth/mis-datos",
			"PUT /api/auth/mis-datos");

	// Solo cuenta como declaración de rol una expresión que nombre roles o permisos.
	private static final Pattern NOMBRA_UN_ROL = Pattern.compile("\\b(hasRole|hasAnyRole|hasAuthority|hasAnyAuthority)\\s*\\(");
	// Si la expresión además deja pasar a cualquiera (o a cualquiera con sesión), el rol no restringe nada.
	private static final Pattern ABRE_A_TODOS = Pattern
			.compile("\\b(permitAll|isAnonymous|isAuthenticated|isFullyAuthenticated|isRememberMe|true)\\b");

	@Autowired
	private RequestMappingHandlerMapping mapeo;

	private static boolean esDeLaAplicacion(HandlerMethod metodo) {
		return metodo.getBeanType().getName().startsWith("com.micomuna.");
	}

	// Los controladores de src/test (RutaProtegidaDePrueba, RutaPorComunaDePrueba) no son parte de la API real.
	// Se reconocen por su ubicación real, no por el nombre ni por una lista:
	//  - por nombre, un controlador de producción que se llame "...DePrueba" se saltearía la revisión;
	//  - con una lista cerrada, si una de esas clases se moviera a src/main también se la saltearía;
	//  - por ubicación, una clase de src/main nunca sale del directorio de clases de prueba, así que no hay forma
	//    de saltear un endpoint real. Se compara contra el directorio de esta misma prueba (y no contra el texto
	//    "test-classes") para que funcione igual con Maven o desde el IDE.
	// Si la ubicación no se puede leer, la clase se revisa como si fuera de producción: un controlador de prueba
	// haría fallar la prueba (falso aviso), nunca al revés.
	private static boolean esDeLasPruebas(HandlerMethod metodo) {
		URL directorioDePruebas = ubicacion(EndpointsDeclaranSuAccesoTests.class);
		URL ubicacionDeLaClase = ubicacion(metodo.getBeanType());
		return directorioDePruebas != null && Objects.equals(directorioDePruebas, ubicacionDeLaClase);
	}

	private static URL ubicacion(Class<?> clase) {
		CodeSource origen = clase.getProtectionDomain().getCodeSource();
		return origen == null ? null : origen.getLocation();
	}

	// Spring Security usa el @PreAuthorize del método si lo hay; si no, el de la clase.
	private static PreAuthorize preAuthorizeVigente(HandlerMethod metodo) {
		PreAuthorize delMetodo = AnnotatedElementUtils.findMergedAnnotation(metodo.getMethod(), PreAuthorize.class);
		return delMetodo != null ? delMetodo
				: AnnotatedElementUtils.findMergedAnnotation(metodo.getBeanType(), PreAuthorize.class);
	}

	static boolean exigeUnRol(String expresion) {
		return NOMBRA_UN_ROL.matcher(expresion).find() && !ABRE_A_TODOS.matcher(expresion).find();
	}

	// "VERBO /patron" -> método que lo atiende, solo de los controladores reales de la aplicación.
	private Map<String, HandlerMethod> endpointsDeLaAplicacion() {
		Map<String, HandlerMethod> endpoints = new LinkedHashMap<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeo.getHandlerMethods().entrySet()) {
			HandlerMethod metodo = entrada.getValue();
			if (!esDeLaAplicacion(metodo) || esDeLasPruebas(metodo)) {
				continue;
			}
			var verbos = entrada.getKey().getMethodsCondition().getMethods();
			for (String patron : entrada.getKey().getPathPatternsCondition().getPatternValues()) {
				for (Object verbo : verbos.isEmpty() ? Set.of("CUALQUIERA") : verbos) {
					endpoints.put(verbo + " " + patron, metodo);
				}
			}
		}
		return endpoints;
	}

	@Test
	void todoEndpointDeclaraQuienPuedeUsarlo() {
		Map<String, HandlerMethod> endpoints = endpointsDeLaAplicacion();
		List<String> sinDeclarar = new ArrayList<>();
		for (Map.Entry<String, HandlerMethod> endpoint : endpoints.entrySet()) {
			String clave = endpoint.getKey();
			String patron = clave.substring(clave.indexOf(' ') + 1);
			PreAuthorize regla = preAuthorizeVigente(endpoint.getValue());
			boolean declarado = PUBLICOS.contains(clave)
					|| CON_SESION.contains(clave)
					|| patron.startsWith("/api/admin/")
					|| (regla != null && exigeUnRol(regla.value()));
			if (!declarado) {
				sinDeclarar.add(clave + (regla == null ? "" : " (@PreAuthorize(\"" + regla.value() + "\") no exige un rol)"));
			}
		}
		assertThat(endpoints).as("se revisó al menos un endpoint").hasSizeGreaterThan(5);
		assertThat(sinDeclarar)
				.as("endpoints sin regla de acceso: agregales @PreAuthorize con hasRole/hasAnyRole, "
						+ "o sumalos a PUBLICOS o CON_SESION si es a propósito")
				.isEmpty();
	}

	@Test
	void lasListasDeAccesoSoloTienenEndpointsQueExisten() {
		// Si un endpoint de PUBLICOS o CON_SESION ya no existe, la lista quedó desactualizada: una entrada vieja
		// podría dejar pasar sin revisión a un endpoint nuevo que se cree con la misma ruta.
		Set<String> existentes = endpointsDeLaAplicacion().keySet();
		assertThat(existentes).as("endpoints de PUBLICOS").containsAll(PUBLICOS);
		assertThat(existentes).as("endpoints de CON_SESION").containsAll(CON_SESION);
	}

	@Test
	void soloCuentaComoReglaDeRolUnaExpresionQueNombraElRol() {
		assertThat(exigeUnRol("hasRole('SUPERADMIN')")).isTrue();
		assertThat(exigeUnRol("hasAnyRole('ADMINISTRADOR', 'ADMINISTRATIVO')")).isTrue();
		assertThat(exigeUnRol("hasAuthority('ROLE_AGENTE')")).isTrue();
		assertThat(exigeUnRol("permitAll()")).isFalse();
		assertThat(exigeUnRol("isAuthenticated()")).isFalse();
		assertThat(exigeUnRol("hasRole('VECINO') or isAuthenticated()")).isFalse();
		assertThat(exigeUnRol("true")).isFalse();
	}


	// MC-17: seguir mis reclamos es solo del vecino. Si alguien cambia el rol de uno de estos endpoints (o lo borra),
	// la prueba falla con el nombre del endpoint.
	private static final Set<String> SOLO_DEL_VECINO = Set.of(
			"GET /api/reclamos/mios",
			"GET /api/reclamos/mios/{numero}",
			"GET /api/reclamos/mios/{numero}/fotos/{idFoto}");

	@Test
	void losEndpointsDeMisReclamosSonSoloDelVecino() {
		Map<String, HandlerMethod> endpoints = endpointsDeLaAplicacion();
		for (String clave : SOLO_DEL_VECINO) {
			assertThat(endpoints).as(clave).containsKey(clave);
			PreAuthorize regla = preAuthorizeVigente(endpoints.get(clave));
			assertThat(regla).as("@PreAuthorize de " + clave).isNotNull();
			assertThat(regla.value()).as(clave).isEqualTo("hasRole('VECINO')");
		}
	}

	// MC-18: el panel de reclamos es solo del personal administrativo y del administrador de la comuna.
	private static final Set<String> SOLO_DEL_PERSONAL = Set.of(
			"GET /api/panel/reclamos",
			"GET /api/panel/reclamos/mapa",
			"GET /api/panel/reclamos/resumen",
			"GET /api/panel/reclamos/{numero}",
			"GET /api/panel/reclamos/{numero}/fotos/{idFoto}",
			"GET /api/panel/agentes",
			"GET /api/panel/metricas",
			"POST /api/panel/reclamos/cambios-de-estado",
			"PUT /api/panel/reclamos/{numero}/agente",
			"PUT /api/panel/reclamos/{numero}/tipo",
			"POST /api/panel/reclamos/descartes");

	@Test
	void losEndpointsDelPanelSonSoloDelPersonalAdministrativo() {
		Map<String, HandlerMethod> endpoints = endpointsDeLaAplicacion();
		for (String clave : SOLO_DEL_PERSONAL) {
			assertThat(endpoints).as(clave).containsKey(clave);
			PreAuthorize regla = preAuthorizeVigente(endpoints.get(clave));
			assertThat(regla).as("@PreAuthorize de " + clave).isNotNull();
			assertThat(regla.value()).as(clave).isEqualTo("hasAnyRole('ADMINISTRATIVO', 'ADMIN_COMUNA')");
		}
		// Y ningún otro endpoint bajo /api/panel quedó afuera de la lista.
		assertThat(endpoints.keySet().stream().filter(k -> k.contains(" /api/panel/")).toList())
				.containsExactlyInAnyOrderElementsOf(SOLO_DEL_PERSONAL);
	}

	// MC-20: los reclamos del agente de campo son solo del agente.
	private static final Set<String> SOLO_DEL_AGENTE = Set.of(
			"GET /api/agente/reclamos",
			"GET /api/agente/reclamos/{numero}",
			"GET /api/agente/reclamos/{numero}/fotos/{idFoto}",
			"POST /api/agente/reclamos/{numero}/empezar",
			"POST /api/agente/reclamos/{numero}/resolver");

	@Test
	void losEndpointsDelAgenteSonSoloDelAgente() {
		Map<String, HandlerMethod> endpoints = endpointsDeLaAplicacion();
		for (String clave : SOLO_DEL_AGENTE) {
			assertThat(endpoints).as(clave).containsKey(clave);
			PreAuthorize regla = preAuthorizeVigente(endpoints.get(clave));
			assertThat(regla).as("@PreAuthorize de " + clave).isNotNull();
			assertThat(regla.value()).as(clave).isEqualTo("hasRole('AGENTE')");
		}
		assertThat(endpoints.keySet().stream().filter(k -> k.contains(" /api/agente/")).toList())
				.containsExactlyInAnyOrderElementsOf(SOLO_DEL_AGENTE);
	}

	@Test
	void losControladoresDePruebaSeReconocenPorSuUbicacion() {
		// Si la detección dejara de andar, los controladores de prueba aparecerían en la revisión (y la harían fallar);
		// esto deja el motivo a la vista. Además, ningún controlador de src/main puede quedar afuera.
		List<String> excluidos = new ArrayList<>();
		List<String> controladoresRealesExcluidos = new ArrayList<>();
		for (HandlerMethod metodo : mapeo.getHandlerMethods().values()) {
			if (esDeLasPruebas(metodo)) {
				excluidos.add(metodo.getBeanType().getSimpleName());
				if (metodo.getBeanType().getPackageName().equals("com.micomuna.controllers")) {
					controladoresRealesExcluidos.add(metodo.getBeanType().getSimpleName());
				}
			}
		}
		assertThat(excluidos).contains("RutaProtegidaDePrueba", "RutaPorComunaDePrueba");
		assertThat(controladoresRealesExcluidos).as("controladores de src/main salteados").isEmpty();
	}
}
