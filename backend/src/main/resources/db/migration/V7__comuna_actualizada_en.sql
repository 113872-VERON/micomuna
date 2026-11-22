-- Fecha de la última modificación de cada comuna (MC-12, ajustes del panel de comunas).
-- Cuenta cualquier cambio: datos, logo, límite o pasar a activa/inactiva. La mantiene el backend al guardar
-- (sin trigger), así el superadmin ve en el panel qué comunas se tocaron hace poco.

-- Se agrega primero como opcional porque la tabla puede tener filas: Oracle no deja crear una columna
-- NOT NULL sin valor en filas existentes. Así funciona igual sobre una base vacía o con comunas cargadas.
ALTER TABLE comuna ADD (actualizada_en TIMESTAMP);

-- Comunas que ya existen: la última modificación conocida es su creación o, si es más nueva, la carga del límite.
UPDATE comuna
   SET actualizada_en = GREATEST(creada_en, NVL(limite_cargado_en, creada_en));

-- Ahora que todas tienen valor, pasa a obligatoria y las filas nuevas toman la fecha actual por defecto.
ALTER TABLE comuna MODIFY (actualizada_en DEFAULT SYSTIMESTAMP NOT NULL);
