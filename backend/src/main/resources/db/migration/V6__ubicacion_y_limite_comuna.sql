-- Ubicación en el mapa (MC-16): dirección aproximada del reclamo y límite geográfico de la comuna.

-- Dirección aproximada que calcula el navegador con Google a partir del pin. Es solo informativa y opcional.
-- Largo en caracteres (CHAR): una tilde ocupa dos bytes.
ALTER TABLE reclamo ADD (direccion VARCHAR2(300 CHAR));

-- Límite de la comuna como texto GeoJSON (Polygon o MultiPolygon). Opcional: sin límite, no se valida la zona.
-- Puede pesar cientos de KB, por eso es un CLOB. El backend no lo carga al leer una comuna
-- (va en una entidad aparte, igual que el logo): la base no necesita nada especial para eso.
-- limite_cargado_en es la fecha de la última carga, para mostrársela al superadmin.
ALTER TABLE comuna ADD (limite CLOB, limite_cargado_en TIMESTAMP);

-- Decisión: ck_reclamo_ubicacion (latitud O referencia) NO se toca. Desde MC-16 el backend exige latitud y
-- longitud en todo reclamo nuevo, y la referencia pasa a ser opcional. La restricción actual ya acepta eso,
-- y sigue aceptando los reclamos viejos con solo referencia. Cambiarla por una ENABLE NOVALIDATE agregaría
-- riesgo con Flyway y Oracle sin ganar nada que el backend no garantice ya.
