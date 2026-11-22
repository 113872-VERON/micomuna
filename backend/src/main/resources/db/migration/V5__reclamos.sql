-- Reclamos (MC-15): catálogo de tipos de problema, el reclamo con su ubicación, hasta 3 fotos
-- y el historial de cambios de estado. Prioridad, duplicados, agente asignado y límite de la comuna
-- llegan con sus propias historias.
-- Los textos que escribe la persona usan largo en caracteres (CHAR): la base mide en bytes por defecto
-- y una tilde ocupa dos, así que "500 de 500" no entraría en un VARCHAR2(500) común.

-- Tipos de problema: iguales para todas las comunas. Es una tabla y no un enum para poder sumar
-- tipos después sin cambiar código. El backend los busca por código, nunca por id.
CREATE TABLE tipo_reclamo (
    id           NUMBER GENERATED ALWAYS AS IDENTITY,
    codigo       VARCHAR2(20)       NOT NULL,
    nombre       VARCHAR2(60 CHAR)  NOT NULL,
    orden        NUMBER(2)          NOT NULL,
    consejo_foto VARCHAR2(300 CHAR) NOT NULL,
    CONSTRAINT pk_tipo_reclamo PRIMARY KEY (id),
    CONSTRAINT uk_tipo_reclamo_codigo UNIQUE (codigo),
    CONSTRAINT uk_tipo_reclamo_orden UNIQUE (orden),
    CONSTRAINT ck_tipo_reclamo_orden CHECK (orden > 0)
);

-- Consejos tomados de la maqueta reportar-problema.html (guía de diseño, paso 2 "Foto").
INSERT INTO tipo_reclamo (codigo, nombre, orden, consejo_foto) VALUES ('BACHE', 'Bache o calle rota', 1,
    'Sacala de día y de cerca, que se vea todo el pozo y un pedazo de la calle.');
INSERT INTO tipo_reclamo (codigo, nombre, orden, consejo_foto) VALUES ('LUZ', 'Luz apagada', 2,
    'Sacá el poste o el farol de día: de noche no se ve nada. Si tiene un número, que se lea.');
INSERT INTO tipo_reclamo (codigo, nombre, orden, consejo_foto) VALUES ('BASURA', 'Basura', 3,
    'Que se vea la basura y el lugar donde está, por ejemplo la esquina o el contenedor.');
INSERT INTO tipo_reclamo (codigo, nombre, orden, consejo_foto) VALUES ('ARBOL', 'Árbol o ramas', 4,
    'Que se vea el árbol entero y lo que molesta: los cables, la vereda o la calle.');
INSERT INTO tipo_reclamo (codigo, nombre, orden, consejo_foto) VALUES ('AGUA', 'Pérdida de agua o desagüe', 5,
    'Sacá el lugar donde sale el agua o donde está tapado el desagüe, y un pedazo de la vereda o de la calle.');
INSERT INTO tipo_reclamo (codigo, nombre, orden, consejo_foto) VALUES ('OTRO', 'Otro problema', 6,
    'Que se vea bien el problema y el lugar donde está.');

-- Contador del número de seguimiento de cada comuna (cada una empieza en 1).
-- El backend lo incrementa con un UPDATE que bloquea la fila de la comuna hasta el COMMIT:
-- dos vecinos que reportan a la vez quedan en fila y nunca reciben el mismo número.
-- Si el alta falla y se deshace, el contador también vuelve atrás (no quedan huecos, a diferencia de una secuencia).
ALTER TABLE comuna ADD (ultimo_numero_reclamo NUMBER(10) DEFAULT 0 NOT NULL);
ALTER TABLE comuna ADD CONSTRAINT ck_comuna_ultimo_numero CHECK (ultimo_numero_reclamo >= 0);

-- Permite que el reclamo exija que su vecino sea de la misma comuna (clave foránea de dos columnas).
-- El id ya es único; esto solo agrega el par para poder referenciarlo.
ALTER TABLE usuario ADD CONSTRAINT uk_usuario_comuna_id UNIQUE (id_comuna, id);

CREATE TABLE reclamo (
    id             NUMBER GENERATED ALWAYS AS IDENTITY,
    id_comuna      NUMBER             NOT NULL,
    numero         NUMBER(10)         NOT NULL,
    id_vecino      NUMBER             NOT NULL,
    id_tipo        NUMBER             NOT NULL,
    descripcion    VARCHAR2(500 CHAR) NOT NULL,
    estado         VARCHAR2(20)       DEFAULT 'RECIBIDO' NOT NULL,
    -- Seis decimales son unos 11 cm: más precisión de la que da el GPS de un celular.
    latitud        NUMBER(8, 6),
    longitud       NUMBER(9, 6),
    referencia     VARCHAR2(200 CHAR),
    creado_en      TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    actualizado_en TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_reclamo PRIMARY KEY (id),
    CONSTRAINT fk_reclamo_comuna FOREIGN KEY (id_comuna) REFERENCES comuna (id),
    -- El vecino tiene que ser de la misma comuna que el reclamo: la base lo garantiza aunque falle el backend.
    CONSTRAINT fk_reclamo_vecino FOREIGN KEY (id_comuna, id_vecino) REFERENCES usuario (id_comuna, id),
    CONSTRAINT fk_reclamo_tipo FOREIGN KEY (id_tipo) REFERENCES tipo_reclamo (id),
    -- Red de seguridad del contador: aunque algo falle, dos reclamos de una comuna no comparten número.
    CONSTRAINT uk_reclamo_comuna_numero UNIQUE (id_comuna, numero),
    CONSTRAINT ck_reclamo_numero CHECK (numero > 0),
    CONSTRAINT ck_reclamo_descripcion CHECK (TRIM(descripcion) IS NOT NULL),
    CONSTRAINT ck_reclamo_estado CHECK (estado IN ('RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO', 'DESCARTADO')),
    CONSTRAINT ck_reclamo_latitud CHECK (latitud BETWEEN -90 AND 90),
    CONSTRAINT ck_reclamo_longitud CHECK (longitud BETWEEN -180 AND 180),
    -- Latitud y longitud van juntas o no va ninguna.
    CONSTRAINT ck_reclamo_lat_lng_juntas CHECK (
        (latitud IS NULL AND longitud IS NULL) OR (latitud IS NOT NULL AND longitud IS NOT NULL)),
    -- Hace falta saber dónde es: el punto del GPS, una referencia escrita, o las dos cosas.
    CONSTRAINT ck_reclamo_ubicacion CHECK (latitud IS NOT NULL OR TRIM(referencia) IS NOT NULL)
);

-- Panel del administrativo (MC-18): reclamos de la comuna filtrados por estado y ordenados por fecha.
CREATE INDEX ix_reclamo_comuna_estado_fecha ON reclamo (id_comuna, estado, creado_en);
-- "Mis reclamos" del vecino (MC-17). Empieza por la comuna porque toda consulta filtra por ella,
-- y además sirve de índice para la clave foránea fk_reclamo_vecino.
CREATE INDEX ix_reclamo_comuna_vecino_fecha ON reclamo (id_comuna, id_vecino, creado_en);

-- Fotos aparte del reclamo: la lista de reclamos no carga las imágenes.
-- Hasta 3 por reclamo: el orden va de 1 a 3 y no se repite. El peso (hasta 2 MB) lo valida la aplicación.
-- Si se borrara un reclamo (no está previsto), sus fotos se borran con él.
CREATE TABLE reclamo_foto (
    id          NUMBER GENERATED ALWAYS AS IDENTITY,
    id_reclamo  NUMBER       NOT NULL,
    orden       NUMBER(1)    NOT NULL,
    imagen      BLOB         NOT NULL,
    imagen_tipo VARCHAR2(20) NOT NULL,
    creada_en   TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_reclamo_foto PRIMARY KEY (id),
    CONSTRAINT fk_reclamo_foto_reclamo FOREIGN KEY (id_reclamo) REFERENCES reclamo (id) ON DELETE CASCADE,
    -- También es el índice para traer las fotos de un reclamo en orden.
    CONSTRAINT uk_reclamo_foto_orden UNIQUE (id_reclamo, orden),
    CONSTRAINT ck_reclamo_foto_orden CHECK (orden BETWEEN 1 AND 3),
    CONSTRAINT ck_reclamo_foto_tipo CHECK (imagen_tipo IN ('image/jpeg', 'image/png'))
);

-- Historial de estados: quién, cuándo y de qué estado a cuál. El alta del reclamo agrega la primera fila
-- (RECIBIDO, sin estado anterior). id_usuario queda vacío si el cambio lo hizo el sistema.
-- Sin borrado en cascada a propósito: el historial es el registro de lo que pasó y no se borra solo.
CREATE TABLE reclamo_estado (
    id              NUMBER GENERATED ALWAYS AS IDENTITY,
    id_reclamo      NUMBER             NOT NULL,
    estado_anterior VARCHAR2(20),
    estado          VARCHAR2(20)       NOT NULL,
    id_usuario      NUMBER,
    observacion     VARCHAR2(500 CHAR),
    cambiado_en     TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_reclamo_estado PRIMARY KEY (id),
    CONSTRAINT fk_reclamo_estado_reclamo FOREIGN KEY (id_reclamo) REFERENCES reclamo (id),
    CONSTRAINT fk_reclamo_estado_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id),
    CONSTRAINT ck_reclamo_estado_estado CHECK (estado IN ('RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO', 'DESCARTADO')),
    CONSTRAINT ck_reclamo_estado_anterior CHECK (estado_anterior IN ('RECIBIDO', 'ASIGNADO', 'EN_REPARACION', 'RESUELTO', 'DESCARTADO'))
);

-- Historial de un reclamo en orden (detalle del reclamo, MC-17 y MC-18).
CREATE INDEX ix_reclamo_estado_reclamo ON reclamo_estado (id_reclamo, cambiado_en);
