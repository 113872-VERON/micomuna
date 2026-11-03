-- Comunas, usuarios y códigos de recuperación de contraseña.
-- El superadmin es el único usuario sin comuna (id_comuna vacío).
CREATE TABLE comuna (
    id        NUMBER GENERATED ALWAYS AS IDENTITY,
    nombre    VARCHAR2(100) NOT NULL,
    provincia VARCHAR2(30)  NOT NULL,
    slug      VARCHAR2(120) NOT NULL,
    activa    NUMBER(1)     DEFAULT 1 NOT NULL,
    creada_en TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_comuna PRIMARY KEY (id),
    CONSTRAINT uk_comuna_slug UNIQUE (slug),
    CONSTRAINT ck_comuna_activa CHECK (activa IN (0, 1))
);

CREATE TABLE usuario (
    id                 NUMBER GENERATED ALWAYS AS IDENTITY,
    id_comuna          NUMBER,
    correo             VARCHAR2(254) NOT NULL,
    clave_hash         VARCHAR2(100) NOT NULL,
    nombre             VARCHAR2(100) NOT NULL,
    rol                VARCHAR2(20)  NOT NULL,
    activo             NUMBER(1)     DEFAULT 1 NOT NULL,
    debe_cambiar_clave NUMBER(1)     DEFAULT 0 NOT NULL,
    creado_en          TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_usuario PRIMARY KEY (id),
    CONSTRAINT fk_usuario_comuna FOREIGN KEY (id_comuna) REFERENCES comuna (id),
    CONSTRAINT uk_usuario_comuna_correo UNIQUE (id_comuna, correo),
    CONSTRAINT ck_usuario_rol CHECK (rol IN ('VECINO', 'ADMINISTRATIVO', 'AGENTE', 'ADMIN_COMUNA', 'SUPERADMIN')),
    CONSTRAINT ck_usuario_activo CHECK (activo IN (0, 1)),
    CONSTRAINT ck_usuario_cambiar CHECK (debe_cambiar_clave IN (0, 1))
);

CREATE TABLE codigo_recuperacion (
    id          NUMBER GENERATED ALWAYS AS IDENTITY,
    id_usuario  NUMBER       NOT NULL,
    codigo_hash VARCHAR2(64) NOT NULL,
    vence_en    TIMESTAMP    NOT NULL,
    usado       NUMBER(1)    DEFAULT 0 NOT NULL,
    CONSTRAINT pk_codigo_recuperacion PRIMARY KEY (id),
    CONSTRAINT fk_codigo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id),
    CONSTRAINT uk_codigo_hash UNIQUE (codigo_hash),
    CONSTRAINT ck_codigo_usado CHECK (usado IN (0, 1))
);
