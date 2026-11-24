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
	public static final String EJECUTOR_DE_IA = "iaExecutor";
	public static final String EJECUTOR_DE_OMISIONES = "iaOmisionesExecutor";

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

	// Los análisis de la IA (MC-24) tienen su propio ejecutor, separado del de correos: un proveedor lento no demora los avisos.
	// Pocos hilos y cola chica: cada análisis puede tardar hasta el tiempo de espera del proveedor. Si la cola se llena,
	// execute lanza RejectedExecutionException y AnalisisIaService deja el reclamo con la sugerencia OMITIDA.
	@Bean(name = EJECUTOR_DE_IA)
	public Executor iaExecutor() {
		ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
		ejecutor.setThreadNamePrefix("ia-");
		ejecutor.setCorePoolSize(2);
		ejecutor.setMaxPoolSize(2);
		ejecutor.setQueueCapacity(20);
		ejecutor.setWaitForTasksToCompleteOnShutdown(true);
		ejecutor.setAwaitTerminationSeconds(5);
		return ejecutor;
	}

	// Escribe las filas OMITIDA cuando la cola de análisis está llena (MC-24), para no hacerlo en el hilo de la petición del vecino.
	// Un solo hilo y cola de 100: son escrituras chicas. Si también se llena, AnalisisIaService solo deja el registro.
	@Bean(name = EJECUTOR_DE_OMISIONES)
	public Executor iaOmisionesExecutor() {
		ThreadPoolTaskExecutor ejecutor = new ThreadPoolTaskExecutor();
		ejecutor.setThreadNamePrefix("ia-omisiones-");
		ejecutor.setCorePoolSize(1);
		ejecutor.setMaxPoolSize(1);
		ejecutor.setQueueCapacity(100);
		ejecutor.setWaitForTasksToCompleteOnShutdown(true);
		ejecutor.setAwaitTerminationSeconds(5);
		return ejecutor;
	}
}
