-- Apariencia de la comuna (color de una paleta cerrada, logo opcional y departamento)
-- y garantía en la base de que existe un solo superadmin.
ALTER TABLE comuna ADD (
    color        VARCHAR2(20) DEFAULT 'CELESTE' NOT NULL,
    departamento VARCHAR2(80),
    logo         BLOB,
    logo_tipo    VARCHAR2(20)
);

ALTER TABLE comuna ADD CONSTRAINT ck_comuna_color CHECK (color IN
    ('CELESTE', 'MARINO', 'VERDE_AZULADO', 'BOSQUE', 'CIRUELA', 'OCRE', 'PIZARRA'));

-- El logo y su tipo van juntos o no va ninguno. El tamaño (hasta 512 KB) lo valida la aplicación.
ALTER TABLE comuna ADD CONSTRAINT ck_comuna_logo CHECK (
    (logo IS NULL AND logo_tipo IS NULL)
    OR (logo IS NOT NULL AND logo_tipo IS NOT NULL AND logo_tipo IN ('image/png', 'image/jpeg')));

-- Solo una fila puede valer 1: las demás dan NULL y Oracle no las indexa.
CREATE UNIQUE INDEX uk_usuario_superadmin_unico
    ON usuario (CASE WHEN rol = 'SUPERADMIN' THEN 1 END);
