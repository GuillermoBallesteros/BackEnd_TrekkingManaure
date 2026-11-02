-- Cédula de ciudadanía (CC): se amplía el margen de 8 a 10 dígitos a 6 a 11 dígitos (sin cero inicial).
-- Solo cambia la CC; el resto de los tipos de documento queda igual. No se edita V18.

UPDATE tipo_documento SET patron = '[1-9][0-9]{5,10}', ayuda = 'Entre 6 y 11 dígitos' WHERE clave = 'CC';

ALTER TABLE persona DROP CONSTRAINT ck_persona_documento_tipo;
ALTER TABLE persona
    ADD CONSTRAINT ck_persona_documento_tipo CHECK (
        CASE tipo_documento
            WHEN 'CC' THEN documento ~ '^[1-9][0-9]{5,10}$'
            WHEN 'TI' THEN documento ~ '^[0-9]{10,11}$'
            WHEN 'RC' THEN documento ~ '^[0-9]{10}$'
            WHEN 'CE' THEN documento ~ '^[0-9]{6,10}$'
            WHEN 'PPT' THEN documento ~ '^[0-9]{5,8}$'
            WHEN 'Pasaporte' THEN documento ~ '^[A-Z0-9]{6,12}$'
            ELSE FALSE
        END);
