package com.micomuna.configs;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

// Cliente HTTP hacia Nominatim (sin el RestClient.Builder autoconfigurado: usa el cliente HTTP simple del JDK) con tiempo de espera de 3 segundos para conectar y 3 para leer.
@Configuration
public class NominatimConfig {

	public static final Duration TIEMPO_DE_ESPERA = Duration.ofSeconds(3);

	@Bean
	RestClient nominatimRestClient(@Value("${nominatim.url}") String url) {
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(TIEMPO_DE_ESPERA);
		fabrica.setReadTimeout(TIEMPO_DE_ESPERA);
		return RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
	}
}
