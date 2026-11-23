package com.micomuna.repositories;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Repository;

import com.micomuna.enums.EstadoReclamo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

// Consultas agregadas de las métricas del panel (MC-21). Todas filtran por la comuna del token y trabajan sobre
// instantes [inicio, fin) ya calculados por el servicio (días de calendario argentino). Ninguna lee imágenes ni arma
// entidades Reclamo: o devuelven cuentas agrupadas o solo dos fechas por fila.
@Repository
public class MetricasRepository {

	// Un reclamo resuelto dentro del rango: su número, cuándo se creó y cuándo se resolvió por última vez.
	public record Resuelto(Integer numero, Instant creadoEn, Instant resueltoEn) {
	}

	// Cuántos reclamos hay y cuántos siguen sin asignar, por tipo (código), entre los creados en el rango.
	public record TotalDeTipo(String tipo, long cantidad, long sinAsignar) {
	}

	@PersistenceContext
	private EntityManager em;

	// Una sola consulta: un grupo por tipo con la cantidad y los que hoy siguen en RECIBIDO.
	public List<TotalDeTipo> totalesPorTipo(Long comunaId, Instant inicio, Instant fin) {
		List<Object[]> filas = em.createQuery("""
				select t.codigo, count(r), sum(case when r.estado = :recibido then 1 else 0 end)
				from Reclamo r join r.tipo t
				where r.comuna.id = :comuna and r.creadoEn >= :inicio and r.creadoEn < :fin
				group by t.codigo
				""", Object[].class)
				.setParameter("recibido", EstadoReclamo.RECIBIDO).setParameter("comuna", comunaId)
				.setParameter("inicio", inicio).setParameter("fin", fin).getResultList();
		List<TotalDeTipo> totales = new ArrayList<>();
		for (Object[] f : filas) {
			totales.add(new TotalDeTipo((String) f[0], ((Number) f[1]).longValue(),
					f[2] == null ? 0 : ((Number) f[2]).longValue()));
		}
		return totales;
	}

	// Una sola consulta para todas las semanas: una suma condicional por tramo [inicio, fin). Devuelve la cantidad de
	// reclamos creados en cada tramo, en el mismo orden. Son pocos tramos (366 días son a lo sumo 53 semanas).
	public List<Long> creadosPorTramo(Long comunaId, List<Instant[]> tramos) {
		StringBuilder jpql = new StringBuilder("select ");
		Map<String, Object> parametros = new LinkedHashMap<>();
		for (int i = 0; i < tramos.size(); i++) {
			if (i > 0) {
				jpql.append(", ");
			}
			jpql.append("sum(case when r.creadoEn >= :i").append(i).append(" and r.creadoEn < :f").append(i)
					.append(" then 1 else 0 end)");
			parametros.put("i" + i, tramos.get(i)[0]);
			parametros.put("f" + i, tramos.get(i)[1]);
		}
		jpql.append(" from Reclamo r where r.comuna.id = :comuna and r.creadoEn >= :desde and r.creadoEn < :hasta");
		Query consulta = em.createQuery(jpql.toString());
		parametros.forEach(consulta::setParameter);
		consulta.setParameter("comuna", comunaId).setParameter("desde", tramos.get(0)[0])
				.setParameter("hasta", tramos.get(tramos.size() - 1)[1]);
		Object resultado = consulta.getSingleResult();
		Object[] sumas = tramos.size() == 1 ? new Object[] { resultado } : (Object[]) resultado;
		List<Long> cantidades = new ArrayList<>();
		for (Object suma : sumas) {
			cantidades.add(suma == null ? 0L : ((Number) suma).longValue());
		}
		return cantidades;
	}

	// De cada reclamo de la comuna, la última vez que pasó a RESUELTO (la fila de historial con el id más alto entre las
	// que pasaron a RESUELTO) si cae dentro del rango, aunque el reclamo se haya creado antes o se haya reabierto después.
	// Devuelve solo número y las dos fechas de cada uno: no hay entidades ni imágenes.
	public List<Resuelto> resueltosEnElRango(Long comunaId, Instant inicio, Instant fin) {
		List<Object[]> filas = em.createQuery("""
				select r.numero, r.creadoEn, e.cambiadoEn
				from ReclamoEstado e join e.reclamo r
				where r.comuna.id = :comuna and e.estado = :resuelto
					and e.id = (select max(e2.id) from ReclamoEstado e2 where e2.reclamo.id = r.id and e2.estado = :resuelto)
					and e.cambiadoEn >= :inicio and e.cambiadoEn < :fin
				""", Object[].class)
				.setParameter("resuelto", EstadoReclamo.RESUELTO).setParameter("comuna", comunaId)
				.setParameter("inicio", inicio).setParameter("fin", fin).getResultList();
		List<Resuelto> resueltos = new ArrayList<>();
		for (Object[] f : filas) {
			resueltos.add(new Resuelto((Integer) f[0], (Instant) f[1], (Instant) f[2]));
		}
		return resueltos;
	}
}
