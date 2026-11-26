package com.micomuna.constants;

// Fragmentos de JPQL de la prioridad efectiva (MC-27). Todos suponen que la consulta llama "r" al reclamo y son texto fijo
// (nunca se les concatena nada que venga del usuario).
// La prioridad efectiva es la que fijó el personal (r.prioridad) y, si no, la sugerida por la IA en la sugerencia vigente:
// la fila más nueva por creada_en y, a igual fecha, por id (igual que findFirstByIdReclamoOrderByCreadaEnDescIdDesc), pero
// solo si está HECHA. Si la vigente es ERROR u OMITIDA, no hay sugerida (no se busca una HECHA más vieja).
public final class Prioridades {

	public static final String SUGERIDA = """
			(select s.prioridadSugerida from SugerenciaIa s where s.idReclamo = r.id
				and s.estado = com.micomuna.enums.EstadoSugerenciaIa.HECHA
				and not exists (select 1 from SugerenciaIa n where n.idReclamo = s.idReclamo
					and (n.creadaEn > s.creadaEn or (n.creadaEn = s.creadaEn and n.id > s.id))))""";

	public static final String EFECTIVA = "coalesce(r.prioridad, " + SUGERIDA + ")";

	// Alta, Media, Baja y al final sin prioridad.
	public static final String ORDEN_POR_PRIORIDAD = "case when " + EFECTIVA + " = com.micomuna.enums.PrioridadIa.ALTA then 1 when "
			+ EFECTIVA + " = com.micomuna.enums.PrioridadIa.MEDIA then 2 when " + EFECTIVA
			+ " = com.micomuna.enums.PrioridadIa.BAJA then 3 else 4 end";

	private Prioridades() {
	}
}
