-- Datos que el vecino ve en los estados Descartado y Resuelto (MC-17).
-- Los escriben después MC-18 (descartar), MC-19 y MC-20 (resolver); la estructura se deja lista ahora
-- porque el vecino ya los muestra en "Mis reclamos".

-- Para que un reclamo pueda apuntar a su original exigiendo la misma comuna (clave foránea de dos columnas,
-- igual que fk_reclamo_vecino). El id ya es único; esto solo agrega el par para poder referenciarlo.
ALTER TABLE reclamo ADD CONSTRAINT uk_reclamo_comuna_id UNIQUE (id_comuna, id);

ALTER TABLE reclamo ADD (
    motivo_descarte      VARCHAR2(30),
    observacion_descarte VARCHAR2(500 CHAR),
    id_reclamo_original  NUMBER,
    trabajo_realizado    VARCHAR2(1000 CHAR)
);

ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_motivo_descarte CHECK (
    motivo_descarte IN ('DUPLICADO', 'NO_ES_DE_LA_COMUNA', 'NO_SE_PUEDE_ATENDER', 'OTRO'));

-- El original tiene que ser de la misma comuna: la base lo garantiza aunque falle el backend.
-- Si id_reclamo_original es NULL la clave no se evalúa (Oracle ignora las claves compuestas con algún NULL).
ALTER TABLE reclamo ADD CONSTRAINT fk_reclamo_original
    FOREIGN KEY (id_comuna, id_reclamo_original) REFERENCES reclamo (id_comuna, id);

-- Un reclamo no puede ser duplicado de sí mismo.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_original_distinto CHECK (id_reclamo_original <> id);

-- Coherencia entre estado y datos de descarte:
-- el motivo solo existe si el reclamo está DESCARTADO, y un DESCARTADO siempre trae motivo.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_descarte_motivo CHECK (
    (estado = 'DESCARTADO' AND motivo_descarte IS NOT NULL)
    OR (estado <> 'DESCARTADO' AND motivo_descarte IS NULL));
-- El reclamo original solo se informa cuando el motivo es DUPLICADO. La base no fuerza que esté presente
-- (esa regla vive en el servicio), pero al descartar por duplicado el backend exige el original de la misma comuna.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_original_duplicado CHECK (
    id_reclamo_original IS NULL OR motivo_descarte = 'DUPLICADO');
-- La observación de la comuna solo acompaña a un motivo (y, por el check anterior, a un descarte).
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_observacion_descarte CHECK (
    observacion_descarte IS NULL OR motivo_descarte IS NOT NULL);

-- No se fuerza que trabajo_realizado exista solo en RESUELTO: si un reclamo resuelto se reabre,
-- el texto puede quedar como antecedente. Solo se evita que sea una cadena vacía.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_trabajo_realizado CHECK (
    trabajo_realizado IS NULL OR TRIM(trabajo_realizado) IS NOT NULL);

-- Buscar los duplicados de un reclamo original. También sirve de índice para fk_reclamo_original.
CREATE INDEX ix_reclamo_original ON reclamo (id_reclamo_original);

-- Fotos del trabajo: las saca el agente al resolver. Conviven con las del vecino en la misma tabla.
ALTER TABLE reclamo_foto ADD (origen VARCHAR2(10) DEFAULT 'VECINO' NOT NULL);
ALTER TABLE reclamo_foto ADD CONSTRAINT ck_reclamo_foto_origen CHECK (origen IN ('VECINO', 'TRABAJO'));

-- Hasta 3 fotos por origen: el orden (1 a 3) no se repite dentro de cada origen.
-- Se crea la nueva antes de borrar la vieja para que id_reclamo (clave foránea) nunca quede sin índice.
ALTER TABLE reclamo_foto ADD CONSTRAINT uk_reclamo_foto_origen_orden UNIQUE (id_reclamo, origen, orden);
ALTER TABLE reclamo_foto DROP CONSTRAINT uk_reclamo_foto_orden;
