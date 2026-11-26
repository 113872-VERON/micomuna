package com.micomuna.repositories;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.micomuna.constants.Prioridades;
import com.micomuna.dtos.AgenteDto;
import com.micomuna.dtos.AgenteConCargaDto;
import com.micomuna.dtos.CambioDeEstadoPanelDto;
import com.micomuna.dtos.PrioridadReclamoDto;
import com.micomuna.dtos.PuntoReclamoDto;
import com.micomuna.dtos.ReclamoPanelDto;
import com.micomuna.entities.Reclamo;
import com.micomuna.enums.EstadoReclamo;
import com.micomuna.enums.OrdenPanel;
import com.micomuna.enums.PrioridadIa;
import com.micomuna.models.FiltrosDelPanel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

// Consultas del panel del personal (MC-18): la lista, el mapa y el resumen comparten los mismos filtros, que son
// opcionales; por eso la consulta se arma por partes. Lo que se concatena son SIEMPRE fragmentos fijos de este archivo:
// todo valor que viene del usuario (estado, tipo, agente, número, texto) va como parámetro enlazado, nunca en el texto.
// Todas filtran por la comuna del token. Ninguna lee imágenes ni arma entidades Reclamo de a una (sin N+1).
@Repository
public class PanelReclamosRepository {

	public static final int MAXIMO_PUNTOS_DEL_MAPA = 1000;
	// Carácter de escape del LIKE: el texto buscado puede traer % y _ y se buscan tal cual.
	private static final char ESCAPE = '!';

	@PersistenceContext
	private EntityManager em;

	private record Donde(String jpql, Map<String, Object> parametros) {
	}

	private static Donde donde(Long comunaId, FiltrosDelPanel f, boolean conEstado) {
		StringBuilder jpql = new StringBuilder(" where r.comuna.id = :comuna");
		Map<String, Object> parametros = new LinkedHashMap<>();
		parametros.put("comuna", comunaId);
		if (conEstado && f.estado() != null) {
			jpql.append(" and r.estado = :estado");
			parametros.put("estado", f.estado());
		}
		if (f.tipo() != null) {
			jpql.append(" and r.tipo.codigo = :tipo");
			parametros.put("tipo", f.tipo());
		}
		if (f.agente() != null) {
			jpql.append(" and r.agente.id = :agente");
			parametros.put("agente", f.agente());
		}
		if (f.numeroImposible()) {
			// Un número que no entra en un entero: no puede existir.
			jpql.append(" and 1 = 0");
		} else if (f.numero() != null) {
			jpql.append(" and r.numero = :numero");
			parametros.put("numero", f.numero());
		}
		if (f.texto() != null) {
			// Sin distinguir mayúsculas ni tildes: se pasa la columna a minúsculas y sin tildes en las vocales y se
			// compara con el texto ya normalizado en Java. translate() es de Oracle; los literales son fijos.
			String sinTildes = "cast(function('translate', lower(r.%s), '" + FiltrosDelPanel.CON_TILDES + "', '"
					+ FiltrosDelPanel.SIN_TILDES + "') as string)";
			jpql.append(" and (").append(sinTildes.formatted("direccion")).append(" like :patron escape '")
					.append(ESCAPE).append("' or ").append(sinTildes.formatted("referencia"))
					.append(" like :patron escape '").append(ESCAPE).append("')");
			parametros.put("patron", "%" + escapar(f.texto()) + "%");
		}
		return new Donde(jpql.toString(), parametros);
	}

	private static String escapar(String texto) {
		StringBuilder resultado = new StringBuilder(texto.length() + 4);
		for (char c : texto.toCharArray()) {
			if (c == ESCAPE || c == '%' || c == '_') {
				resultado.append(ESCAPE);
			}
			resultado.append(c);
		}
		return resultado.toString();
	}

	private static void enlazar(Query consulta, Donde donde) {
		donde.parametros().forEach(consulta::setParameter);
	}

	// ---- Lista

	// Una sola consulta para la página: trae el agente, el original y la cantidad de duplicados como columnas
	// (subconsulta de conteo), sin una consulta extra por fila.
	public List<ReclamoPanelDto> listar(Long comunaId, FiltrosDelPanel filtros, OrdenPanel orden, int pagina, int tamanio) {
		Donde donde = donde(comunaId, filtros, true);
		Query consulta = em.createQuery("""
				select r.numero, t.codigo, t.nombre, r.estado, r.descripcion, r.direccion, r.referencia, r.creadoEn,
					a.id, a.nombre, o.numero,
					(select count(d) from Reclamo d where d.reclamoOriginal.id = r.id
						and d.estado = com.micomuna.enums.EstadoReclamo.DESCARTADO
						and d.motivoDescarte = com.micomuna.enums.MotivoDescarte.DUPLICADO),
					r.prioridad, """ + Prioridades.SUGERIDA + " " + """
				from Reclamo r join r.tipo t left join r.agente a left join r.reclamoOriginal o
				""" + donde.jpql() + (orden == OrdenPanel.PRIORIDAD
						? " order by " + Prioridades.ORDEN_POR_PRIORIDAD + ", r.creadoEn, r.id"
						: " order by r.creadoEn desc, r.id desc"), Object[].class);
		enlazar(consulta, donde);
		consulta.setFirstResult(pagina * tamanio);
		consulta.setMaxResults(tamanio);
		List<ReclamoPanelDto> filas = new ArrayList<>();
		for (Object fila : consulta.getResultList()) {
			Object[] c = (Object[]) fila;
			AgenteDto agente = c[8] == null ? null : new AgenteDto((Long) c[8], (String) c[9]);
			filas.add(new ReclamoPanelDto((Integer) c[0], (String) c[1], (String) c[2], (EstadoReclamo) c[3],
					(String) c[4], (String) c[5], (String) c[6], (Instant) c[7], agente, (Integer) c[10],
					((Number) c[11]).intValue(), PrioridadReclamoDto.de((PrioridadIa) c[12], (PrioridadIa) c[13])));
		}
		return filas;
	}

	public long contar(Long comunaId, FiltrosDelPanel filtros) {
		Donde donde = donde(comunaId, filtros, true);
		Query consulta = em.createQuery("select count(r) from Reclamo r" + donde.jpql());
		enlazar(consulta, donde);
		return ((Number) consulta.getSingleResult()).longValue();
	}

	// ---- Mapa

	// Hasta MAXIMO + 1 puntos, los más nuevos: el servicio usa el uno de más para saber si hubo que recortar.
	public List<PuntoReclamoDto> puntos(Long comunaId, FiltrosDelPanel filtros) {
		Donde donde = donde(comunaId, filtros, true);
		Query consulta = em.createQuery("select r.numero, r.estado, t.codigo, r.latitud, r.longitud "
				+ "from Reclamo r join r.tipo t" + donde.jpql()
				+ " and r.latitud is not null and r.longitud is not null order by r.creadoEn desc, r.id desc",
				Object[].class);
		enlazar(consulta, donde);
		consulta.setMaxResults(MAXIMO_PUNTOS_DEL_MAPA + 1);
		List<PuntoReclamoDto> puntos = new ArrayList<>();
		for (Object fila : consulta.getResultList()) {
			Object[] c = (Object[]) fila;
			puntos.add(new PuntoReclamoDto((Integer) c[0], (EstadoReclamo) c[1], (String) c[2],
					((BigDecimal) c[3]).doubleValue(), ((BigDecimal) c[4]).doubleValue()));
		}
		return puntos;
	}

	// ---- Resumen

	// Cantidad por estado con los filtros salvo el de estado (solo hay filas de los estados que tienen reclamos).
	public Map<EstadoReclamo, Long> contarPorEstado(Long comunaId, FiltrosDelPanel filtros) {
		Donde donde = donde(comunaId, filtros, false);
		Query consulta = em.createQuery("select r.estado, count(r) from Reclamo r" + donde.jpql() + " group by r.estado",
				Object[].class);
		enlazar(consulta, donde);
		Map<EstadoReclamo, Long> cantidades = new LinkedHashMap<>();
		for (Object fila : consulta.getResultList()) {
			Object[] c = (Object[]) fila;
			cantidades.put((EstadoReclamo) c[0], ((Number) c[1]).longValue());
		}
		return cantidades;
	}

	// Reclamos RECIBIDOS de la comuna, sin filtros: cuántos son y la fecha del más viejo.
	public SinAsignar sinAsignar(Long comunaId) {
		Object[] c = (Object[]) em.createQuery(
				"select count(r), min(r.creadoEn) from Reclamo r where r.comuna.id = :comuna and r.estado = :estado",
				Object[].class).setParameter("comuna", comunaId).setParameter("estado", EstadoReclamo.RECIBIDO)
				.getSingleResult();
		return new SinAsignar(((Number) c[0]).longValue(), (Instant) c[1]);
	}

	public record SinAsignar(long cantidad, Instant masAntiguo) {
	}

	// ---- Detalle

	// Todo lo que el detalle muestra del reclamo en una sola consulta (tipo, vecino, agente y original); sin
	// colecciones ni imágenes.
	public Optional<Reclamo> detalle(Long comunaId, Integer numero) {
		return em.createQuery("""
				select r from Reclamo r join fetch r.tipo join fetch r.vecino left join fetch r.agente
					left join fetch r.reclamoOriginal
				where r.comuna.id = :comuna and r.numero = :numero
				""", Reclamo.class).setParameter("comuna", comunaId).setParameter("numero", numero)
				.getResultStream().findFirst();
	}

	// Números de los reclamos descartados como duplicados de este.
	public List<Integer> numerosDeDuplicados(Long idOriginal) {
		return em.createQuery("""
				select d.numero from Reclamo d where d.reclamoOriginal.id = :id
					and d.estado = com.micomuna.enums.EstadoReclamo.DESCARTADO order by d.numero
				""", Integer.class).setParameter("id", idOriginal).getResultList();
	}

	// Historial para el personal, del más viejo al más nuevo, con quién hizo cada cambio, la nota interna y el agente.
	// El agente anterior de una reasignación NO está guardado: se deriva recorriendo las filas en orden y recordando
	// quién estaba a cargo (la fila que asigna trae su agente; volver a RECIBIDO o descartar lo quita). Es lo más
	// confiable porque sale de lo que quedó escrito y no de un dato calculado aparte que podría desfasarse. Si una fila
	// vieja (anterior a MC-19) asignó sin guardar el agente, no hay "anterior" para mostrar en la siguiente.
	public List<CambioDeEstadoPanelDto> historialParaElPersonal(Long idReclamo, Long comunaId) {
		List<Object[]> filas = em.createQuery("""
				select e.estadoAnterior, e.estado, e.cambiadoEn, u.nombre, e.observacion, a.id, a.nombre
				from ReclamoEstado e left join e.usuario u left join e.agente a
				where e.reclamo.id = :id and e.reclamo.comuna.id = :comuna order by e.cambiadoEn, e.id
				""", Object[].class).setParameter("id", idReclamo).setParameter("comuna", comunaId).getResultList();
		List<CambioDeEstadoPanelDto> historial = new ArrayList<>();
		AgenteDto aCargo = null;
		for (Object[] c : filas) {
			EstadoReclamo estado = (EstadoReclamo) c[1];
			AgenteDto agente = c[5] == null ? null : new AgenteDto((Long) c[5], (String) c[6]);
			AgenteDto anterior = null;
			if (agente != null) {
				if (aCargo != null && !aCargo.id().equals(agente.id())) {
					anterior = aCargo;
				}
				aCargo = agente;
			} else if (estado == EstadoReclamo.RECIBIDO || estado == EstadoReclamo.DESCARTADO) {
				aCargo = null;
			}
			historial.add(new CambioDeEstadoPanelDto((EstadoReclamo) c[0], estado, (Instant) c[2], (String) c[3],
					(String) c[4], agente, anterior));
		}
		return historial;
	}

	// Agentes que se pueden asignar (activos, con contraseña) y cuántos reclamos tiene cada uno en curso (ASIGNADO o
	// EN_REPARACION) en la comuna. Una sola consulta: el conteo sale del left join, no de una consulta por agente.
	public List<AgenteConCargaDto> agentesConCarga(Long comunaId) {
		List<Object[]> filas = em.createQuery("""
				select u.id, u.nombre, count(r)
				from Usuario u left join Reclamo r on r.agente.id = u.id and r.comuna.id = :comuna
					and r.estado in (com.micomuna.enums.EstadoReclamo.ASIGNADO, com.micomuna.enums.EstadoReclamo.EN_REPARACION)
				where u.comuna.id = :comuna and u.rol = com.micomuna.enums.Rol.AGENTE and u.activo = true
					and u.claveHash is not null
				group by u.id, u.nombre
				""", Object[].class).setParameter("comuna", comunaId).getResultList();
		List<AgenteConCargaDto> agentes = new ArrayList<>();
		for (Object[] c : filas) {
			agentes.add(new AgenteConCargaDto((Long) c[0], (String) c[1], ((Number) c[2]).intValue()));
		}
		return agentes;
	}
}
