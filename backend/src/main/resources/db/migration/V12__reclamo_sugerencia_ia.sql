-- Sugerencias de la IA sobre un reclamo (MC-24). La IA solo sugiere: la decisión es del personal municipal,
-- y esta tabla guarda lo que sugirió para poder compararlo después con lo que decidió el personal.
-- Una fila por análisis. Si se reanaliza el reclamo se agrega otra fila; la vigente es la más nueva
-- (por eso no hay UNIQUE por reclamo y el historial de análisis se conserva).
CREATE TABLE reclamo_sugerencia_ia (
    id                 NUMBER GENERATED ALWAYS AS IDENTITY,
    id_comuna          NUMBER             NOT NULL,
    id_reclamo         NUMBER             NOT NULL,
    -- HECHA: la IA respondió. ERROR: falló la llamada. OMITIDA: no se llamó (tope diario o IA apagada).
    -- Guardar también ERROR y OMITIDA permite contar intentos y explicar por qué un reclamo no tiene sugerencia.
    estado             VARCHAR2(12)       NOT NULL,
    tipo_sugerido      VARCHAR2(20),
    prioridad_sugerida VARCHAR2(5),
    justificacion      VARCHAR2(500 CHAR),
    -- Quién respondió (CLAUDE, OLLAMA) o NINGUNO si no se llamó a nadie. El modelo exacto sirve para comparar calidad.
    proveedor          VARCHAR2(20)       NOT NULL,
    modelo             VARCHAR2(60),
    -- Solo con ERROR. El backend no debe volcar acá datos personales (texto del vecino, correo, etc.).
    detalle_error      VARCHAR2(300 CHAR),
    -- Tokens que consumió el análisis, para medir el costo. Opcionales: el modelo local o una omisión no los informan.
    tokens_entrada     NUMBER,
    tokens_salida      NUMBER,
    creada_en          TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_reclamo_sugerencia_ia PRIMARY KEY (id),
    -- Clave de dos columnas (usa uk_reclamo_comuna_id, V9): la comuna de la sugerencia es la del reclamo.
    -- id_comuna está acá para contar el tope diario por comuna sin hacer join con reclamo.
    -- ON DELETE CASCADE: es un dato derivado (se puede volver a generar) y no tiene valor sin su reclamo.
    -- A diferencia del historial de estados, que es un registro de lo que pasó y no se borra solo.
    CONSTRAINT fk_sugerencia_ia_reclamo FOREIGN KEY (id_comuna, id_reclamo)
        REFERENCES reclamo (id_comuna, id) ON DELETE CASCADE,
    -- El tipo sugerido tiene que ser un código real de tipo_reclamo: así, si se suma un tipo, la IA puede
    -- sugerirlo sin cambiar la base (un CHECK con los seis códigos habría que tocarlo cada vez).
    CONSTRAINT fk_sugerencia_ia_tipo FOREIGN KEY (tipo_sugerido) REFERENCES tipo_reclamo (codigo),
    CONSTRAINT ck_sugerencia_ia_estado CHECK (estado IN ('HECHA', 'ERROR', 'OMITIDA')),
    CONSTRAINT ck_sugerencia_ia_prioridad CHECK (prioridad_sugerida IN ('ALTA', 'MEDIA', 'BAJA')),
    -- Coherencia: una HECHA trae tipo, prioridad y justificación; ERROR y OMITIDA no traen ninguno de los tres
    -- (no hay sugerencia que guardar).
    CONSTRAINT ck_sugerencia_ia_hecha CHECK (
        (estado = 'HECHA' AND tipo_sugerido IS NOT NULL AND prioridad_sugerida IS NOT NULL
            AND TRIM(justificacion) IS NOT NULL)
        OR (estado <> 'HECHA' AND tipo_sugerido IS NULL AND prioridad_sugerida IS NULL AND justificacion IS NULL)),
    -- El detalle del error solo existe con ERROR (y no es obligatorio).
    CONSTRAINT ck_sugerencia_ia_detalle CHECK (detalle_error IS NULL OR estado = 'ERROR'),
    CONSTRAINT ck_sugerencia_ia_tokens CHECK (
        (tokens_entrada IS NULL OR tokens_entrada >= 0) AND (tokens_salida IS NULL OR tokens_salida >= 0))
);

-- La sugerencia vigente de un reclamo: la fila más nueva.
-- (La clave foránea empieza por id_comuna: la cubre el índice siguiente.)
CREATE INDEX ix_sugerencia_ia_reclamo_fecha ON reclamo_sugerencia_ia (id_reclamo, creada_en);

-- Tope diario por comuna: contar los análisis de hoy sin join. Sirve además de índice de fk_sugerencia_ia_reclamo.
CREATE INDEX ix_sugerencia_ia_comuna_fecha ON reclamo_sugerencia_ia (id_comuna, creada_en);

-- No se indexa fk_sugerencia_ia_tipo: tipo_reclamo es un catálogo chico cuyos códigos no se borran ni cambian.
