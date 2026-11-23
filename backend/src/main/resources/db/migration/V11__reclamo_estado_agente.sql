-- Agente que quedó asignado con cada cambio de estado (MC-19), para que el historial pueda decir
-- "Asignado a Marcos" o "Reasignado de Ana a Marcos". El agente anterior no se guarda: sale de la fila previa
-- del mismo reclamo. Es opcional: los cambios que no tocan al agente y las filas viejas quedan en NULL.
ALTER TABLE reclamo_estado ADD (id_agente NUMBER);

-- Clave foránea simple a usuario (id). No se pudo garantizar "misma comuna" desde la base como en reclamo:
-- reclamo_estado no tiene id_comuna, y agregarlo sería duplicar un dato que ya está en el reclamo.
-- Que el agente sea de la comuna del reclamo y tenga rol AGENTE lo controla el backend al asignar;
-- además reclamo.id_agente (fk_reclamo_agente, V10) ya fuerza la misma comuna en el dato vigente.
ALTER TABLE reclamo_estado ADD CONSTRAINT fk_reclamo_estado_agente
    FOREIGN KEY (id_agente) REFERENCES usuario (id);

-- No se crea índice para la clave foránea: nadie busca el historial por agente y los usuarios no se borran
-- ni cambian de id, que son los únicos casos en que Oracle lo usaría. Si más adelante hace falta
-- "todo lo que hizo un agente", se agrega con su propia migración.

-- "Reclamos en curso por agente" (ASIGNADO o EN_REPARACION, por comuna) no necesita índice nuevo:
-- ix_reclamo_comuna_estado_fecha (id_comuna, estado, creado_en) recorre solo esos dos estados de la comuna
-- (son pocos frente al total) y lee id_agente de cada fila. Si el volumen crece, el candidato es
-- (id_comuna, estado, id_agente), que respondería el conteo sin tocar la tabla.
