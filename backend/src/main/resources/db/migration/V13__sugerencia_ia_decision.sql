-- Decisión del personal sobre la sugerencia de la IA (MC-25). La IA solo sugiere: acá queda registrado si el
-- personal administrativo la aceptó tal cual o la corrigió, qué tipo quedó, quién decidió y cuándo.
-- Sirve para medir cuánto acierta la IA (ACEPTADA vs. CORREGIDA) y para dejar constancia de quién decidió.
-- La decisión vale para la sugerencia vigente del reclamo (la fila más nueva, ver V12): las anteriores
-- quedan como historial de análisis y no se deciden.
-- Las cuatro columnas son opcionales: las filas existentes y los inserts del backend anterior (que no las
-- conoce) siguen siendo válidos y quedan "sin decidir".
ALTER TABLE reclamo_sugerencia_ia ADD (
    decision            VARCHAR2(10),
    tipo_decidido       VARCHAR2(20),
    id_usuario_decision NUMBER,
    decidida_en         TIMESTAMP
);

-- El tipo decidido es un código real de tipo_reclamo (igual criterio que tipo_sugerido en V12).
ALTER TABLE reclamo_sugerencia_ia ADD CONSTRAINT fk_sugerencia_ia_tipo_decidido
    FOREIGN KEY (tipo_decidido) REFERENCES tipo_reclamo (codigo);

-- Clave foránea de dos columnas (igual que fk_reclamo_agente, V10): quien decidió es de la misma comuna que la
-- sugerencia. Usa uk_usuario_comuna_id (V5). Con id_usuario_decision NULL la clave no se evalúa.
-- Que ese usuario tenga un rol con permiso para decidir NO lo controla la base (el rol está en otra columna
-- de usuario y un CHECK no puede mirar otra tabla): lo valida el servicio del backend.
ALTER TABLE reclamo_sugerencia_ia ADD CONSTRAINT fk_sugerencia_ia_usuario_decision
    FOREIGN KEY (id_comuna, id_usuario_decision) REFERENCES usuario (id_comuna, id);

ALTER TABLE reclamo_sugerencia_ia ADD CONSTRAINT ck_sugerencia_ia_decision
    CHECK (decision IN ('ACEPTADA', 'CORREGIDA'));

-- Coherencia: o no hay decisión (las cuatro columnas en NULL) o están las cuatro. Solo se decide sobre una
-- sugerencia HECHA (ERROR y OMITIDA no tienen nada que aceptar). ACEPTADA deja el tipo sugerido y CORREGIDA
-- lo cambia por otro distinto: así la comparación con la IA no queda ambigua.
ALTER TABLE reclamo_sugerencia_ia ADD CONSTRAINT ck_sugerencia_ia_decision_coherente CHECK (
    (decision IS NULL AND tipo_decidido IS NULL AND id_usuario_decision IS NULL AND decidida_en IS NULL)
    OR (decision IS NOT NULL AND tipo_decidido IS NOT NULL AND id_usuario_decision IS NOT NULL
        AND decidida_en IS NOT NULL AND estado = 'HECHA'
        AND ((decision = 'ACEPTADA' AND tipo_decidido = tipo_sugerido)
             OR (decision = 'CORREGIDA' AND tipo_decidido <> tipo_sugerido))));

-- No se crean índices para las claves foráneas nuevas: tipo_reclamo es un catálogo cuyos códigos no cambian,
-- los usuarios no se borran ni cambian de id, y nadie busca sugerencias por quién decidió. Si más adelante
-- hace falta "todo lo que decidió una persona", se agrega con su propia migración.
