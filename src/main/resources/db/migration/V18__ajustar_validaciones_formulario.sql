-- Validaciones del formulario (documento "Validaciones del formulario de inscripción"). El backend normaliza antes de
-- guardar; aquí se ajustan los catálogos, se convierten los datos que ya existieran y se añaden las restricciones
-- CHECK sobre el valor ya normalizado (última defensa). No se edita ninguna migración anterior.

-- ---------------------------------------------------------------------------------------------------------------
-- 1. Catálogo de documentos: cédula de ciudadanía de 8 a 10 dígitos sin cero inicial, PPT de 5 a 8 dígitos y
--    pasaporte de 6 a 12 letras o números. TI, RC y CE no cambian.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE tipo_documento SET patron = '[1-9][0-9]{7,9}', ayuda = 'Entre 8 y 10 dígitos' WHERE clave = 'CC';
UPDATE tipo_documento SET patron = '[0-9]{5,8}', ayuda = 'Entre 5 y 8 dígitos' WHERE clave = 'PPT';
UPDATE tipo_documento SET patron = '[A-Za-z0-9]{6,12}', ayuda = 'Entre 6 y 12 letras o números' WHERE clave = 'Pasaporte';

-- ---------------------------------------------------------------------------------------------------------------
-- 2. Teléfonos de Colombia: el fijo (solo contacto de emergencia) es 60X + 7 dígitos, con X en 1, 2, 4, 5, 6, 7 u 8
--    (marcación única nacional, Resolución 5826 de 2019 de la CRC). El celular no cambia.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE pais_telefono SET fijo_inicios = ARRAY['601', '602', '604', '605', '606', '607', '608']::VARCHAR(3)[]
WHERE iso = 'CO';

-- ---------------------------------------------------------------------------------------------------------------
-- 3. Catálogo de EPS: se añaden las que figuran en el listado del Ministerio de Salud y faltaban, y las opciones
--    especiales ("Régimen especial", "Otra", "No tengo EPS") pasan al final. Ninguna EPS existente se quita ni se
--    renombra: las reservas ya guardadas las referencian.
-- ---------------------------------------------------------------------------------------------------------------
UPDATE eps SET orden = orden + 4 WHERE orden >= 23;
INSERT INTO eps (clave, pide_nombre, orden) VALUES
    ('Salud Mía', FALSE, 23),
    ('SOS (Servicio Occidental de Salud)', FALSE, 24),
    ('EPS Familiar de Colombia', FALSE, 25),
    ('AIC (Asociación Indígena del Cauca)', FALSE, 26);

-- ---------------------------------------------------------------------------------------------------------------
-- 4. Conversión de los datos existentes a la forma normalizada (la misma que aplica el backend):
--    nombres y contacto en MAYÚSCULAS y sin tildes (la Ñ se conserva), ciudad y EPS "Otra" sin tildes, correo en
--    minúsculas y sin espacios, documento sin separadores y teléfonos solo con dígitos.
-- ---------------------------------------------------------------------------------------------------------------
-- Quita las tildes pero conserva la Ñ: se protege con un marcador porque unaccent también la convertiría en N.
CREATE FUNCTION pg_temp.sin_tildes(t text) RETURNS text AS $$
    SELECT replace(replace(unaccent(replace(replace(t, 'Ñ', E'\u0001'), 'ñ', E'\u0002')), E'\u0001', 'Ñ'), E'\u0002', 'ñ')
$$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION pg_temp.espacios(t text) RETURNS text AS $$
    SELECT regexp_replace(btrim(t), '\s+', ' ', 'g')
$$ LANGUAGE sql IMMUTABLE;

UPDATE persona SET
    nombres = upper(replace(pg_temp.sin_tildes(pg_temp.espacios(nombres)), 'ñ', 'Ñ')),
    apellidos = upper(replace(pg_temp.sin_tildes(pg_temp.espacios(apellidos)), 'ñ', 'Ñ')),
    ciudad = pg_temp.sin_tildes(pg_temp.espacios(ciudad)),
    eps_otra = pg_temp.sin_tildes(pg_temp.espacios(eps_otra)),
    correo = lower(regexp_replace(correo, '\s', '', 'g')),
    documento = upper(regexp_replace(documento, '[.\-\s]', '', 'g')),
    telefono_numero = regexp_replace(telefono_numero, '\D', '', 'g');

UPDATE contacto_emergencia SET
    nombre = upper(replace(pg_temp.sin_tildes(pg_temp.espacios(nombre)), 'ñ', 'Ñ')),
    telefono_numero = regexp_replace(telefono_numero, '\D', '', 'g');

-- ---------------------------------------------------------------------------------------------------------------
-- 5. Restricciones CHECK sobre el valor normalizado. Las columnas VARCHAR no se alteran: el largo lo fijan los CHECK.
-- ---------------------------------------------------------------------------------------------------------------
ALTER TABLE persona
    ADD CONSTRAINT ck_persona_nombres CHECK (
        char_length(nombres) BETWEEN 2 AND 40 AND nombres ~ '^[A-ZÑ]+([ ''-][A-ZÑ]+)*$'),
    ADD CONSTRAINT ck_persona_apellidos CHECK (
        char_length(apellidos) BETWEEN 2 AND 40 AND apellidos ~ '^[A-ZÑ]+([ ''-][A-ZÑ]+)*$'),
    ADD CONSTRAINT ck_persona_ciudad CHECK (
        char_length(ciudad) BETWEEN 3 AND 40
        AND ciudad ~ '^[A-Za-zÑñ]+(\.[A-Za-zÑñ]+)*\.?([ ''-]{1,2}[A-Za-zÑñ]+(\.[A-Za-zÑñ]+)*\.?)*$'),
    ADD CONSTRAINT ck_persona_eps_otra_texto CHECK (
        eps_otra IS NULL
        OR (char_length(eps_otra) BETWEEN 3 AND 40 AND eps_otra ~ '^[A-Za-zÑñ]+( [A-Za-zÑñ]+)*$')),
    ADD CONSTRAINT ck_persona_correo_largo CHECK (char_length(correo) <= 100),
    ADD CONSTRAINT ck_persona_documento_tipo CHECK (
        CASE tipo_documento
            WHEN 'CC' THEN documento ~ '^[1-9][0-9]{7,9}$'
            WHEN 'TI' THEN documento ~ '^[0-9]{10,11}$'
            WHEN 'RC' THEN documento ~ '^[0-9]{10}$'
            WHEN 'CE' THEN documento ~ '^[0-9]{6,10}$'
            WHEN 'PPT' THEN documento ~ '^[0-9]{5,8}$'
            WHEN 'Pasaporte' THEN documento ~ '^[A-Z0-9]{6,12}$'
            ELSE FALSE
        END);

ALTER TABLE contacto_emergencia
    ADD CONSTRAINT ck_contacto_nombre CHECK (
        char_length(nombre) BETWEEN 5 AND 80 AND nombre ~ '^[A-ZÑ]+([ ''-][A-ZÑ]+)*$');
