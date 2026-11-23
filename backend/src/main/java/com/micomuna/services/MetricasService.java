package com.micomuna.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.micomuna.constants.Fechas;
import com.micomuna.dtos.CantidadPorSemanaDto;
import com.micomuna.dtos.CantidadPorTipoDto;
import com.micomuna.dtos.MetricasDto;
import com.micomuna.entities.TipoReclamo;
import com.micomuna.exceptions.PeticionInvalidaException;
import com.micomuna.repositories.MetricasRepository;
import com.micomuna.repositories.TipoReclamoRepository;
import com.micomuna.security.ComunaActual;

import lombok.RequiredArgsConstructor;

// Métricas simples del panel (MC-21). Los días son de calendario argentino y el rango incluye los dos extremos: desde
// las 00:00 del primer día hasta las 24:00 del último. La comuna sale siempre del token (ComunaActual).
// Este servicio NO controla roles: el controlador exige el personal.
@Service
@RequiredArgsConstructor
public class MetricasService {

	private static final Logger log = LoggerFactory.getLogger(MetricasService.class);

	public static final int DIAS_POR_DEFECTO = 30;
	public static final int DIAS_MAXIMOS = 366;

	private final MetricasRepository metricas;
	private final TipoReclamoRepository tipos;
	private final ComunaActual comunaActual;
	private final Clock reloj;

	// desde y hasta llegan como texto (AAAA-MM-DD) para poder responder VALIDACION si están mal escritas. Sin ninguna de
	// las dos: los últimos 30 días hasta hoy (hoy incluido). Con una sola: VALIDACION.
	@Transactional(readOnly = true)
	public MetricasDto calcular(String desdeTexto, String hastaTexto) {
		LocalDate desde = fecha(desdeTexto, "desde");
		LocalDate hasta = fecha(hastaTexto, "hasta");
		if ((desde == null) != (hasta == null)) {
			throw new PeticionInvalidaException("VALIDACION", "Mandá las dos fechas (desde y hasta) o ninguna.");
		}
		if (desde == null) {
			hasta = LocalDate.now(reloj);
			desde = hasta.minusDays(DIAS_POR_DEFECTO - 1L);
		}
		if (desde.isAfter(hasta)) {
			throw new PeticionInvalidaException("FECHAS_INVALIDAS", "La fecha desde no puede ser posterior a la fecha hasta.");
		}
		long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
		if (dias > DIAS_MAXIMOS) {
			throw new PeticionInvalidaException("RANGO_MUY_LARGO", "El rango puede tener hasta " + DIAS_MAXIMOS + " días.");
		}

		Long comuna = comunaActual.id();
		Instant inicio = comienzoDelDia(desde);
		Instant fin = comienzoDelDia(hasta.plusDays(1));

		// 1) Por tipo (y sin asignar), en el orden de los tipos, con 0 donde no hubo.
		Map<String, MetricasRepository.TotalDeTipo> totales = new HashMap<>();
		metricas.totalesPorTipo(comuna, inicio, fin).forEach(t -> totales.put(t.tipo(), t));
		List<CantidadPorTipoDto> porTipo = new ArrayList<>();
		long creados = 0;
		long sinAsignar = 0;
		for (TipoReclamo tipo : tipos.findAllByOrderByOrdenAsc()) {
			MetricasRepository.TotalDeTipo total = totales.get(tipo.getCodigo());
			long cantidad = total == null ? 0 : total.cantidad();
			porTipo.add(new CantidadPorTipoDto(tipo.getCodigo(), tipo.getNombre(), cantidad));
			creados += cantidad;
			sinAsignar += total == null ? 0 : total.sinAsignar();
		}

		// 2) Por semana (lunes a domingo, recortadas al rango).
		List<LocalDate[]> semanas = semanas(desde, hasta);
		List<Instant[]> tramos = new ArrayList<>();
		for (LocalDate[] s : semanas) {
			tramos.add(new Instant[] { comienzoDelDia(s[0]), comienzoDelDia(s[1].plusDays(1)) });
		}
		List<Long> cantidades = metricas.creadosPorTramo(comuna, tramos);
		List<CantidadPorSemanaDto> porSemana = new ArrayList<>();
		for (int i = 0; i < semanas.size(); i++) {
			porSemana.add(new CantidadPorSemanaDto(semanas.get(i)[0], semanas.get(i)[1], cantidades.get(i)));
		}

		// 3) Resueltos en el rango y su tiempo promedio (de la creación a esa resolución).
		List<MetricasRepository.Resuelto> resueltos = metricas.resueltosEnElRango(comuna, inicio, fin);
		Double promedio = null;
		if (!resueltos.isEmpty()) {
			long milisegundos = 0;
			for (MetricasRepository.Resuelto r : resueltos) {
				milisegundos += milisegundosDeResolucion(comuna, r);
			}
			promedio = BigDecimal.valueOf(milisegundos)
					.divide(BigDecimal.valueOf(resueltos.size() * 3_600_000L), 1, RoundingMode.HALF_UP).doubleValue();
		}
		return new MetricasDto(desde, hasta, (int) dias, creados, resueltos.size(), promedio, sinAsignar, porTipo, porSemana);
	}

	// Lo que tardó en resolverse. Si la resolución es anterior a la creación (un dato mal cargado) no se puede restar tiempo:
	// cuenta como 0 y queda un aviso en el log, con el número de reclamo y la comuna (sin datos personales).
	static long milisegundosDeResolucion(Long comuna, MetricasRepository.Resuelto r) {
		long milisegundos = r.resueltoEn().toEpochMilli() - r.creadoEn().toEpochMilli();
		if (milisegundos < 0) {
			log.warn("Métricas: el reclamo n.º {} de la comuna {} figura resuelto antes de haberse creado ({} ms); se cuenta como 0.",
					r.numero(), comuna, milisegundos);
			return 0;
		}
		return milisegundos;
	}

	// Cada semana va de lunes a domingo; la primera y la última se recortan al rango.
	static List<LocalDate[]> semanas(LocalDate desde, LocalDate hasta) {
		List<LocalDate[]> semanas = new ArrayList<>();
		LocalDate inicio = desde;
		while (!inicio.isAfter(hasta)) {
			LocalDate domingo = inicio.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			LocalDate fin = domingo.isAfter(hasta) ? hasta : domingo;
			semanas.add(new LocalDate[] { inicio, fin });
			inicio = fin.plusDays(1);
		}
		return semanas;
	}

	private static Instant comienzoDelDia(LocalDate dia) {
		return dia.atStartOfDay(Fechas.ARGENTINA).toInstant();
	}

	private static LocalDate fecha(String texto, String campo) {
		if (texto == null || texto.isBlank()) {
			return null;
		}
		try {
			return LocalDate.parse(texto.strip());
		} catch (DateTimeParseException e) {
			throw new PeticionInvalidaException("VALIDACION", "La fecha «" + campo + "» tiene que ser AAAA-MM-DD.");
		}
	}
}
