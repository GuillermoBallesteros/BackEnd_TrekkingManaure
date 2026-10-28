-- HU-N01: catalogos de solo lectura, sembrados con shared/reservas/data/catalogos.ts del front.

CREATE TABLE tipo_documento (
    clave     VARCHAR(20) PRIMARY KEY,
    patron    VARCHAR(40) NOT NULL,
    ayuda     VARCHAR(60) NOT NULL,
    edad_min  SMALLINT,
    edad_max  SMALLINT,
    orden     SMALLINT    NOT NULL,
    CONSTRAINT ck_tipo_documento_edades CHECK (edad_min IS NULL OR edad_max IS NULL OR edad_min <= edad_max)
);
INSERT INTO tipo_documento (clave, patron, ayuda, edad_min, edad_max, orden) VALUES
    ('CC',        '[0-9]{6,10}',       'Entre 6 y 10 dígitos',          18,   NULL, 1),
    ('CE',        '[0-9]{6,10}',       'Entre 6 y 10 dígitos',          NULL, NULL, 2),
    ('TI',        '[0-9]{10,11}',      '10 u 11 dígitos',               7,    18,   3),
    ('RC',        '[0-9]{10}',         '10 dígitos',                    NULL, 7,    4),
    ('Pasaporte', '[A-Za-z0-9]{5,20}', 'Entre 5 y 20 letras o números', NULL, NULL, 5),
    ('PPT',       '[A-Za-z0-9]{5,15}', 'Entre 5 y 15 letras o números', NULL, NULL, 6);

CREATE TABLE genero (
    clave VARCHAR(30) PRIMARY KEY,
    orden SMALLINT    NOT NULL
);
INSERT INTO genero (clave, orden) VALUES
    ('Femenino', 1), ('Masculino', 2), ('Otro', 3), ('Prefiero no decirlo', 4);

CREATE TABLE talla (
    clave VARCHAR(5) PRIMARY KEY,
    orden SMALLINT   NOT NULL
);
INSERT INTO talla (clave, orden) VALUES
    ('XS', 1), ('S', 2), ('M', 3), ('L', 4), ('XL', 5), ('XXL', 6), ('XXXL', 7);

CREATE TABLE estadia (
    clave      VARCHAR(30) PRIMARY KEY,
    ocupa_cupo BOOLEAN     NOT NULL,
    orden      SMALLINT    NOT NULL
);
INSERT INTO estadia (clave, ocupa_cupo, orden) VALUES
    ('Páramo', TRUE, 1), ('Hotel en Manaure', FALSE, 2), ('Regreso a casa', FALSE, 3);

CREATE TABLE extra (
    clave                VARCHAR(30) PRIMARY KEY,
    etiqueta             VARCHAR(60) NOT NULL,
    requiere_acompanante BOOLEAN     NOT NULL,
    orden                SMALLINT    NOT NULL
);
INSERT INTO extra (clave, etiqueta, requiere_acompanante, orden) VALUES
    ('cuatrimoto',           'Paseo en cuatrimoto',                FALSE, 1),
    ('parapente',            'Vuelo en parapente del titular',     FALSE, 2),
    ('parapenteAcompanante', 'Vuelo en parapente del acompañante', TRUE,  3);

CREATE TABLE eps (
    clave       VARCHAR(60) PRIMARY KEY,
    pide_nombre BOOLEAN     NOT NULL,
    orden       SMALLINT    NOT NULL
);
INSERT INTO eps (clave, pide_nombre, orden) VALUES
    ('Nueva EPS', FALSE, 1),
    ('EPS Sura', FALSE, 2),
    ('EPS Sanitas', FALSE, 3),
    ('Salud Total', FALSE, 4),
    ('Compensar', FALSE, 5),
    ('Famisanar', FALSE, 6),
    ('Coosalud', FALSE, 7),
    ('Mutual SER', FALSE, 8),
    ('Cajacopi', FALSE, 9),
    ('Asmet Salud', FALSE, 10),
    ('Emssanar', FALSE, 11),
    ('Savia Salud', FALSE, 12),
    ('Capresoca', FALSE, 13),
    ('Comfenalco Valle', FALSE, 14),
    ('Capital Salud', FALSE, 15),
    ('Aliansalud', FALSE, 16),
    ('Dusakawi', FALSE, 17),
    ('Anas Wayuu', FALSE, 18),
    ('Pijaos Salud', FALSE, 19),
    ('Mallamas', FALSE, 20),
    ('Comfachocó', FALSE, 21),
    ('Comfaoriente', FALSE, 22),
    ('Régimen especial o de excepción', FALSE, 23),
    ('Otra', TRUE, 24),
    ('No tengo EPS', FALSE, 25);

CREATE TABLE parentesco (
    clave                VARCHAR(40) PRIMARY KEY,
    es_responsable_legal BOOLEAN     NOT NULL,
    orden                SMALLINT    NOT NULL
);
INSERT INTO parentesco (clave, es_responsable_legal, orden) VALUES
    ('Madre', TRUE, 1),
    ('Padre', TRUE, 2),
    ('Tutor legal o acudiente', TRUE, 3),
    ('Cónyuge o pareja', FALSE, 4),
    ('Hijo o hija', FALSE, 5),
    ('Hermano o hermana', FALSE, 6),
    ('Abuelo o abuela', FALSE, 7),
    ('Tío o tía', FALSE, 8),
    ('Primo o prima', FALSE, 9),
    ('Otro familiar', FALSE, 10),
    ('Amigo o amiga', FALSE, 11);

CREATE TABLE pais_telefono (
    iso           CHAR(2)      PRIMARY KEY,
    nombre        VARCHAR(40)  NOT NULL,
    indicativo    VARCHAR(4)   NOT NULL,
    ejemplo       VARCHAR(20)  NOT NULL,
    movil_largos  SMALLINT[]   NOT NULL,
    movil_inicios VARCHAR(3)[] NOT NULL,
    fijo_largos   SMALLINT[],
    fijo_inicios  VARCHAR(3)[],
    orden         SMALLINT     NOT NULL,
    CONSTRAINT ck_pais_telefono_fijo CHECK ((fijo_largos IS NULL) = (fijo_inicios IS NULL))
);
INSERT INTO pais_telefono (iso, nombre, indicativo, ejemplo, movil_largos, movil_inicios, fijo_largos, fijo_inicios, orden) VALUES
    ('CO', 'Colombia',             '57',  '300 123 4567',   ARRAY[10]::SMALLINT[],    ARRAY['3'],                                  ARRAY[10]::SMALLINT[], ARRAY['60'],                                  1),
    ('VE', 'Venezuela',            '58',  '412 123 4567',   ARRAY[10]::SMALLINT[],    ARRAY['41','42'],                            ARRAY[10]::SMALLINT[], ARRAY['2'],                                   2),
    ('EC', 'Ecuador',              '593', '99 123 4567',    ARRAY[9]::SMALLINT[],     ARRAY['9'],                                  ARRAY[8]::SMALLINT[],  ARRAY['2','3','4','5','6','7'],               3),
    ('PE', 'Perú',                 '51',  '912 345 678',    ARRAY[9]::SMALLINT[],     ARRAY['9'],                                  ARRAY[8]::SMALLINT[],  ARRAY['1','4','5','6','7','8'],               4),
    ('MX', 'México',               '52',  '55 1234 5678',   ARRAY[10]::SMALLINT[],    ARRAY['1','2','3','4','5','6','7','8','9'],  NULL,                  NULL,                                 5),
    ('US', 'Estados Unidos',       '1',   '212 555 0123',   ARRAY[10]::SMALLINT[],    ARRAY['2','3','4','5','6','7','8','9'],      NULL,                  NULL,                                 6),
    ('ES', 'España',               '34',  '612 34 56 78',   ARRAY[9]::SMALLINT[],     ARRAY['6','7'],                              ARRAY[9]::SMALLINT[],  ARRAY['8','9'],                               7),
    ('AR', 'Argentina',            '54',  '9 11 1234 5678', ARRAY[10,11]::SMALLINT[], ARRAY['1','2','3','9'],                    NULL,                  NULL,                                 8),
    ('CL', 'Chile',                '56',  '9 1234 5678',    ARRAY[9]::SMALLINT[],     ARRAY['9'],                                  ARRAY[9]::SMALLINT[],  ARRAY['2','3','4','5','6','7'],               9),
    ('BR', 'Brasil',               '55',  '11 91234 5678',  ARRAY[11]::SMALLINT[],    ARRAY['1','2','3','4','5','6','7','8','9'],  ARRAY[10]::SMALLINT[], ARRAY['1','2','3','4','5','6','7','8','9'],   10),
    ('PA', 'Panamá',               '507', '6123 4567',      ARRAY[8]::SMALLINT[],     ARRAY['6'],                                  ARRAY[7]::SMALLINT[],  ARRAY['2','3','4','5','7','8','9'],           11),
    ('CR', 'Costa Rica',           '506', '8312 3456',      ARRAY[8]::SMALLINT[],     ARRAY['5','6','7','8'],                      ARRAY[8]::SMALLINT[],  ARRAY['2','4'],                               12),
    ('DO', 'República Dominicana', '1',   '809 123 4567',   ARRAY[10]::SMALLINT[],    ARRAY['809','829','849'],                    NULL,                  NULL,                                 13);
