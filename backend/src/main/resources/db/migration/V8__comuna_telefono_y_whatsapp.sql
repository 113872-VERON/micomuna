-- Teléfono y WhatsApp de contacto de cada comuna (MC-76).
-- El formulario de la comuna los pedía desde MC-12 pero no se guardaban; los usa MC-17 para mostrarle
-- al vecino los botones de llamar y de WhatsApp.
-- Son opcionales. Se guardan solo con dígitos y con el código de país (formato internacional E.164 sin el "+"),
-- de 8 a 15 dígitos, por ejemplo 5493515551234. El CHECK deja pasar NULL (una comuna puede no tener uno de los dos).

ALTER TABLE comuna ADD (
    telefono VARCHAR2(20),
    whatsapp VARCHAR2(20)
);

ALTER TABLE comuna ADD CONSTRAINT ck_comuna_telefono CHECK (REGEXP_LIKE(telefono, '^[0-9]{8,15}$'));
ALTER TABLE comuna ADD CONSTRAINT ck_comuna_whatsapp CHECK (REGEXP_LIKE(whatsapp, '^[0-9]{8,15}$'));
