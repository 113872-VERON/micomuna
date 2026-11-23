-- Agente asignado al reclamo y índices del panel del personal administrativo (MC-18).

-- Agente de campo asignado. Opcional: un reclamo RECIBIDO todavía no tiene agente.
-- Clave foránea de dos columnas (igual que fk_reclamo_vecino): el agente tiene que ser de la misma comuna
-- que el reclamo, y la base lo garantiza aunque falle el backend. Usa uk_usuario_comuna_id (V5).
-- Si id_agente es NULL la clave no se evalúa (Oracle ignora las claves compuestas con algún NULL).
-- Que ese usuario tenga rol AGENTE NO lo controla la base: el rol vive en otra columna de usuario y
-- un CHECK no puede mirar otra tabla. Lo valida el servicio del backend al asignar.
ALTER TABLE reclamo ADD (id_agente NUMBER);
ALTER TABLE reclamo ADD CONSTRAINT fk_reclamo_agente
    FOREIGN KEY (id_comuna, id_agente) REFERENCES usuario (id_comuna, id);

-- A propósito NO hay un CHECK que obligue a tener agente en ASIGNADO o EN_REPARACION: la base de desarrollo
-- ya tiene reclamos ASIGNADO sin agente (datos de prueba anteriores a esta columna) y el CHECK haría fallar
-- la migración. Esa regla la aplica el servicio al asignar y al cambiar de estado.

-- Índices del panel. Ya existen ix_reclamo_comuna_estado_fecha (id_comuna, estado, creado_en), que sirve
-- para filtrar por estado ordenado por fecha y para los contadores por estado, y uk_reclamo_comuna_numero
-- (id_comuna, numero), que sirve para buscar por número. No se repiten.

-- Filtro por agente (y de paso índice de la clave foránea fk_reclamo_agente), ordenado por fecha.
CREATE INDEX ix_reclamo_comuna_agente_fecha ON reclamo (id_comuna, id_agente, creado_en);

-- Lista de la comuna sin filtro de estado, ordenada por fecha de creación descendente: los índices
-- anteriores empiezan por estado o vecino y no sirven para ese orden.
CREATE INDEX ix_reclamo_comuna_fecha ON reclamo (id_comuna, creado_en);

-- No se indexa el tipo: cada comuna tiene pocos reclamos por tipo y se filtra junto con otros criterios;
-- si más adelante pesa, se agrega con su propia migración.
