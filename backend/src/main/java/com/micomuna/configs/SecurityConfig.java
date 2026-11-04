package com.micomuna.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
public class SecurityConfig {

	// Sin sesión: cada petición trae su token en "Authorization: Bearer".
	// CSRF queda desactivado porque no se usan cookies. Los permisos por rol se agregan en cada endpoint.
	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, UsuarioRepository usuarios)
			throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/comunas/*").permitAll()
						.requestMatchers("/api/salud", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(new JwtAuthFilter(jwt, usuarios), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}
}
