package com.micomuna.configs;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// El envío de correos de avisos (MC-22) va en hilos aparte, acotados, para no hacer esperar a quien hizo el cambio.
@Configuration
@EnableAsync
public class AsyncConfig {

	public static final String EJECUTOR_DE_CORREO = "correoExecutor";

	@Bean(name = EJECUTOR_DE_CORREO)
	public Executor correoExecutor() {
		ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
		ejecutor.setThreadNamePrefix("correo-");
		ejecutor.setCorePoolSize(2);
		ejecutor.setMaxPoolSize(4);
		ejecutor.setQueueCapacity(200);
		// Si la cola se llena, execute lanza RejectedExecutionException: AvisosDeReclamoService la atiende y registra
		// el número de reclamo y la comuna del aviso perdido (el cambio ya está confirmado y no debe fallar).
		ejecutor.setWaitForTasksToCompleteOnShutdown(true);
		ejecutor.setAwaitTerminationSeconds(10);
		return ejecutor;
	}
}
