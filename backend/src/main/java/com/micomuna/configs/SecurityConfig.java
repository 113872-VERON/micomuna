package com.micomuna.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.micomuna.repositories.UsuarioRepository;
import com.micomuna.security.JwtAuthFilter;
import com.micomuna.security.JwtService;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	// Sin sesión: cada petición trae su token en "Authorization: Bearer".
	// CSRF queda desactivado porque no se usan cookies. Los permisos por rol se agregan en cada endpoint.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, UsuarioRepository usuarios,
			Environment entorno) throws Exception {
		// La documentación de la API (Swagger) solo se publica en desarrollo.
		boolean desarrollo = entorno.acceptsProfiles(Profiles.of("dev"));
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
						// Mismo cuerpo que el de ApiExceptionHandler para los 403 que decide el filtro y no un controlador.
						.accessDeniedHandler((pedido, respuesta, error) -> {
							respuesta.setStatus(HttpStatus.FORBIDDEN.value());
							respuesta.setContentType("application/problem+json");
							respuesta.setCharacterEncoding("UTF-8");
							respuesta.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Acceso denegado\",\"status\":403,"
									+ "\"detail\":\"No tenés permiso para hacer esto\",\"codigo\":\"ACCESO_DENEGADO\"}");
						}))
				.authorizeHttpRequests(auth -> {
					if (desarrollo) {
						auth.requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/swagger-ui/**", "/swagger-ui.html")
								.permitAll();
					}
					auth
						.requestMatchers(HttpMethod.GET, "/api/comunas/*", "/api/comunas/*/logo").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/comunas/*/registro", "/api/comunas/*/login",
								"/api/comunas/*/recuperar-clave", "/api/comunas/*/restablecer-clave",
								"/api/comunas/*/aceptar-invitacion", "/api/admin/login").permitAll()
						.requestMatchers("/api/salud").permitAll()
						// Defensa en profundidad: todo lo de /api/admin es del superadmin, además del @PreAuthorize de cada controlador.
						.requestMatchers("/api/admin/**").hasRole("SUPERADMIN")
						// Igual con la gestión de usuarios de una comuna: además, así un rol sin permiso recibe 403
						// antes de que se valide el cuerpo (si no, un cuerpo inválido respondería 400).
						.requestMatchers("/api/usuarios", "/api/usuarios/**").hasRole("ADMIN_COMUNA")
						// Reportar un problema es solo del vecino. Solo el alta: las otras rutas de reclamos (listados, detalle)
						// llegan con otras historias y otros roles, cada una con su @PreAuthorize.
						.requestMatchers(HttpMethod.POST, "/api/reclamos").hasRole("VECINO")
						// Seguir mis reclamos (MC-17, lista, detalle y fotos) tambien es solo del vecino.
						.requestMatchers(HttpMethod.GET, "/api/reclamos/mios", "/api/reclamos/mios/**").hasRole("VECINO")
						// El panel de reclamos (MC-18) es solo del personal administrativo y del administrador de la comuna.
						.requestMatchers("/api/panel/**").hasAnyRole("ADMINISTRATIVO", "ADMIN_COMUNA")
						// Direccion de un punto: vecinos y personal de una comuna, no el superadmin. Por URL, para que el
						// superadmin reciba 403 antes de que se validen los parametros.
						.requestMatchers("/api/ubicacion/**").hasAnyRole("VECINO", "AGENTE", "ADMINISTRATIVO", "ADMIN_COMUNA")
						.anyRequest().authenticated();
				})
				.addFilterBefore(new JwtAuthFilter(jwt, usuarios), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
