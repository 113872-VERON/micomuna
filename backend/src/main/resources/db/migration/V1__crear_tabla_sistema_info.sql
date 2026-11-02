-- Primera migración: comprueba que Flyway funciona y deja un dato mínimo del sistema.
CREATE TABLE sistema_info (
    clave VARCHAR2(50)  NOT NULL,
    valor VARCHAR2(200) NOT NULL,
    CONSTRAINT pk_sistema_info PRIMARY KEY (clave)
);

INSERT INTO sistema_info (clave, valor) VALUES ('proyecto', 'MiComuna');
