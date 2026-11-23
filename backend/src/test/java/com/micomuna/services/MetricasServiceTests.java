package com.micomuna.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.micomuna.repositories.MetricasRepository.Resuelto;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

// MC-21: un tiempo de resolución negativo (dato mal cargado) no se esconde: cuenta como 0 y queda un aviso en el log.
class MetricasServiceTests {

	private Logger logger;
	private ListAppender<ILoggingEvent> avisos;

	@BeforeEach
	void escucharElLog() {
		logger = (Logger) LoggerFactory.getLogger(MetricasService.class);
		avisos = new ListAppender<>();
		avisos.start();
		logger.addAppender(avisos);
	}

	@AfterEach
	void dejarDeEscuchar() {
		logger.detachAppender(avisos);
	}

	@Test
	void unTiempoNegativoCuentaComoCeroYDejaUnAvisoConNumeroYComuna() {
		Instant creado = Instant.parse("2026-11-10T12:00:00Z");
		Resuelto alReves = new Resuelto(42, creado, creado.minusSeconds(3600));

		assertThat(MetricasService.milisegundosDeResolucion(7L, alReves)).isZero();

		assertThat(avisos.list).hasSize(1);
		ILoggingEvent aviso = avisos.list.get(0);
		assertThat(aviso.getLevel()).isEqualTo(Level.WARN);
		assertThat(aviso.getFormattedMessage()).contains("42").contains("comuna 7");
	}

	@Test
	void unTiempoNormalSeCuentaTalCualYNoAvisa() {
		Instant creado = Instant.parse("2026-11-10T12:00:00Z");

		assertThat(MetricasService.milisegundosDeResolucion(7L, new Resuelto(1, creado, creado.plusSeconds(7200))))
				.isEqualTo(7_200_000L);
		assertThat(MetricasService.milisegundosDeResolucion(7L, new Resuelto(2, creado, creado))).isZero();
		assertThat(avisos.list).isEmpty();
	}
}
