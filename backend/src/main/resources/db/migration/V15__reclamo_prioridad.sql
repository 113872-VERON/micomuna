-- Prioridad que fijó el personal sobre un reclamo (MC-27). La IA solo sugiere (prioridad_sugerida, V12): acá queda
-- únicamente lo que decidió una persona, con quién y cuándo (mismo criterio de trazabilidad que V13 y V14).
-- La prioridad efectiva NO se guarda: la calcula el backend como la del reclamo si existe; si no, la
-- prioridad_sugerida de la sugerencia vigente (la fila más nueva de reclamo_sugerencia_ia) cuando está HECHA;
-- si no, ninguna. La sugerencia de la IA no se copia al reclamo para que siga siendo solo una sugerencia
-- y para poder comparar después lo que sugirió la IA con lo que decidió el personal.
-- Las tres columnas son opcionales: los reclamos existentes quedan "sin prioridad fijada" y siguen siendo válidos.
ALTER TABLE reclamo ADD (
    prioridad               VARCHAR2(5),
    id_usuario_prioridad    NUMBER,
    prioridad_fijada_en     TIMESTAMP
);

-- Mismos valores que prioridad_sugerida (ck_sugerencia_ia_prioridad, V12), para poder compararlas directamente.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_prioridad CHECK (prioridad IN ('ALTA', 'MEDIA', 'BAJA'));

-- Clave foránea de dos columnas (igual que fk_reclamo_usuario_no_duplicado, V14): quien fijó la prioridad es de la
-- misma comuna que el reclamo, y la base lo garantiza aunque falle el backend. Usa uk_usuario_comuna_id (V5).
-- Con id_usuario_prioridad NULL la clave no se evalúa.
-- Que ese usuario sea ADMINISTRATIVO o ADMIN_COMUNA NO lo controla la base (el rol está en otra columna de
-- usuario y un CHECK no puede mirar otra tabla): lo valida el servicio del backend.
ALTER TABLE reclamo ADD CONSTRAINT fk_reclamo_usuario_prioridad
    FOREIGN KEY (id_comuna, id_usuario_prioridad) REFERENCES usuario (id_comuna, id);

-- Prioridad, quién y cuándo van juntos o no va ninguno.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_prioridad_completa CHECK (
    (prioridad IS NULL AND id_usuario_prioridad IS NULL AND prioridad_fijada_en IS NULL)
    OR (prioridad IS NOT NULL AND id_usuario_prioridad IS NOT NULL AND prioridad_fijada_en IS NOT NULL));

-- Historial: no hace falta cambiar nada. El cambio de tipo de MC-25 guarda una fila de reclamo_estado con
-- estado_anterior = estado = el estado actual del reclamo, el usuario que lo hizo y el texto en observacion
-- ("Cambió el tipo de X a Y"). La prioridad usa lo mismo ("Cambió la prioridad de X a Y"): reclamo_estado no tiene
-- CHECK sobre observacion (solo sobre los estados, V5) y entra en sus 500 caracteres, sin columnas ni tipos de
-- evento nuevos.

-- Índices: NO se agrega ninguno. El orden por prioridad efectiva (Alta, Media, Baja, sin prioridad; a igual, el más
-- viejo) depende de una expresión que mezcla reclamo.prioridad con la sugerencia vigente de otra tabla
-- (COALESCE + CASE sobre una subconsulta), y un índice de una sola tabla no puede ordenar por eso. Lo que sí hace
-- el índice es acotar las filas: los filtros de la lista (comuna, estado, agente) ya entran por
-- ix_reclamo_comuna_estado_fecha (V5) y ix_reclamo_comuna_agente_fecha (V10), y la sugerencia vigente de cada
-- reclamo se busca por ix_sugerencia_ia_reclamo_fecha (id_reclamo, creada_en, V12). Una comuna chica tiene
-- cientos o pocos miles de reclamos, y ordenar esa cantidad en memoria cuesta milisegundos.
-- Un índice sobre reclamo.prioridad tampoco ayudaría: es una columna con tres valores y mayormente vacía (la
-- mayoría de las prioridades vienen de la IA), y encarecería cada alta. Si el volumen crece, se evalúa en su propia
-- migración materializar la prioridad efectiva en una columna indexable.
-- Tampoco se indexa la clave foránea nueva: nadie busca reclamos por quién fijó la prioridad.
