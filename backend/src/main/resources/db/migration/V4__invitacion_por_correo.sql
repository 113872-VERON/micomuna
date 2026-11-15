-- Invitación por correo en lugar de clave temporal: el usuario nuevo queda sin contraseña (pendiente)
-- y elige la suya con un enlace de un solo uso. Quien lo crea nunca conoce una clave.

-- Ya no hay clave temporal que obligue a cambiarla en el primer ingreso.
ALTER TABLE usuario DROP CONSTRAINT ck_usuario_cambiar;
ALTER TABLE usuario DROP COLUMN debe_cambiar_clave;

-- Mientras la invitación está pendiente, clave_hash queda vacío (en Oracle, el texto vacío es nulo).
ALTER TABLE usuario MODIFY (clave_hash NULL);

-- Los códigos de un solo uso sirven para recuperar la clave (60 minutos) o para aceptar una invitación (72 horas).
ALTER TABLE codigo_recuperacion ADD (tipo VARCHAR2(20) DEFAULT 'RECUPERACION' NOT NULL);
ALTER TABLE codigo_recuperacion ADD CONSTRAINT ck_codigo_tipo CHECK (tipo IN ('RECUPERACION', 'INVITACION'));
