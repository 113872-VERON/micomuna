-- Decisión "es un reclamo nuevo, no es duplicado" del personal sobre un reclamo (MC-26).
-- Cuando el sistema avisa de posibles duplicados cercanos (regla del backend, no se guarda la lista), el
-- personal puede vincular el reclamo al original (descarte DUPLICADO, V9) o dejarlo como nuevo. Esto último
-- queda registrado acá, con quién y cuándo (mismo criterio de trazabilidad que V13). Después de marcarlo, el
-- aviso no vuelve a aparecer para ese reclamo. No se puede deshacer: no lo pide la historia.
-- Las dos columnas son opcionales: los reclamos existentes quedan "sin revisar" y siguen siendo válidos.
ALTER TABLE reclamo ADD (
    id_usuario_no_duplicado NUMBER,
    no_duplicado_en         TIMESTAMP
);

-- Clave foránea de dos columnas (igual que fk_reclamo_agente, V10): quien lo marcó es de la misma comuna
-- que el reclamo, y la base lo garantiza aunque falle el backend. Usa uk_usuario_comuna_id (V5).
-- Con id_usuario_no_duplicado NULL la clave no se evalúa.
-- Que ese usuario sea ADMINISTRATIVO o ADMIN_COMUNA NO lo controla la base (el rol está en otra columna de
-- usuario y un CHECK no puede mirar otra tabla): lo valida el servicio del backend.
ALTER TABLE reclamo ADD CONSTRAINT fk_reclamo_usuario_no_duplicado
    FOREIGN KEY (id_comuna, id_usuario_no_duplicado) REFERENCES usuario (id_comuna, id);

-- Quién y cuándo van juntos o no va ninguno.
ALTER TABLE reclamo ADD CONSTRAINT ck_reclamo_no_duplicado CHECK (
    (id_usuario_no_duplicado IS NULL AND no_duplicado_en IS NULL)
    OR (id_usuario_no_duplicado IS NOT NULL AND no_duplicado_en IS NOT NULL));

-- Índice para la búsqueda de candidatos: NO se agrega. La consulta filtra por id_comuna, estado abierto
-- (RECIBIDO, ASIGNADO, EN_REPARACION) y id_tipo, y después aplica la caja de latitud/longitud. Con
-- ix_reclamo_comuna_estado_fecha (id_comuna, estado, creado_en, V5) Oracle ya entra directo a los reclamos
-- abiertos de la comuna, y el tipo y la caja se filtran sobre esas pocas filas. Una comuna chica tiene
-- cientos o pocos miles de reclamos abiertos, así que un índice con tipo o coordenadas no mejora nada
-- apreciable y encarece cada alta. Además la consulta se hace solo al abrir el detalle de un reclamo.
-- Si el volumen crece, se agrega uno (id_comuna, id_tipo, estado, latitud, longitud) en su propia migración.
-- Tampoco se indexa la clave foránea nueva: nadie busca reclamos por quién los marcó como no duplicados.
