package com.micomuna.configs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springdoc.core.customizers.OperationCustomizer;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	@Bean
	OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info().title("MiComuna API").version("0.0.1")
						.description("Gestor de reclamos municipales"))
				.components(new Components().addSecuritySchemes("bearerAuth",
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
	}

	// Documenta en todos los endpoints protegidos lo que Spring Security responde por su cuenta:
	// 401 sin sesión (o con el token vencido o inválido) y, si el endpoint exige un rol (@PreAuthorize), 403.
	@Bean
	OperationCustomizer respuestasDeAcceso() {
		return (operacion, metodo) -> {
			boolean protegido = operacion.getSecurity() != null && !operacion.getSecurity().isEmpty();
			if (!protegido) {
				return operacion;
			}
			operacion.getResponses().addApiResponse("401", new ApiResponse()
					.description("Sin sesión, o el token venció o no es válido"));
			boolean exigeRol = AnnotatedElementUtils.hasAnnotation(metodo.getMethod(), PreAuthorize.class)
					|| AnnotatedElementUtils.hasAnnotation(metodo.getBeanType(), PreAuthorize.class);
			if (exigeRol) {
				operacion.getResponses().addApiResponse("403", new ApiResponse()
						.description("Tu rol no permite esto (ACCESO_DENEGADO)"));
			}
			return operacion;
		};
	}
}
