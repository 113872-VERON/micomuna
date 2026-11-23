package com.micomuna.configs;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.micomuna.constants.Fechas;

// El reloj del negocio, en hora de Argentina. Se inyecta para que las pruebas puedan fijar "hoy".
@Configuration
public class RelojConfig {

	@Bean
	public Clock reloj() {
		return Clock.system(Fechas.ARGENTINA);
	}
}
