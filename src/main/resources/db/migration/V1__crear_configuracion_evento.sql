CREATE TABLE configuracion_evento (
    id                          SMALLINT     PRIMARY KEY CHECK (id = 1),
    nombre_evento               VARCHAR(150),
    fecha_evento                DATE,
    inscripciones_desde         DATE,
    inscripciones_hasta         DATE,
    whatsapp_numero             VARCHAR(15)  CHECK (whatsapp_numero ~ '^[0-9]{10,15}$'),
    precio_con_kit              BIGINT       NOT NULL CHECK (precio_con_kit >= 0),
    precio_acompanante_sin_kit  BIGINT       NOT NULL CHECK (precio_acompanante_sin_kit >= 0),
    precio_cuatritour           BIGINT       NOT NULL CHECK (precio_cuatritour >= 0),
    precio_parapente            BIGINT       NOT NULL CHECK (precio_parapente >= 0),
    valor_reserva               BIGINT       NOT NULL CHECK (valor_reserva > 0 AND valor_reserva <= precio_con_kit),
    dias_limite_compromiso      INT          NOT NULL CHECK (dias_limite_compromiso >= 0),
    edad_minima                 INT          NOT NULL CHECK (edad_minima >= 0),
    tallas                      VARCHAR(5)[] NOT NULL,
    cupo_maximo                 INT          CHECK (cupo_maximo >= 0),
    CONSTRAINT ck_config_rango_inscripciones
        CHECK (inscripciones_desde IS NULL OR inscripciones_hasta IS NULL OR inscripciones_desde < inscripciones_hasta),
    CONSTRAINT ck_config_hasta_antes_evento
        CHECK (inscripciones_hasta IS NULL OR fecha_evento IS NULL OR inscripciones_hasta <= fecha_evento)
);

INSERT INTO configuracion_evento (id, precio_con_kit, precio_acompanante_sin_kit, precio_cuatritour,
                                  precio_parapente, valor_reserva, dias_limite_compromiso, edad_minima, tallas)
VALUES (1, 350000, 150000, 150000, 200000, 50000, 14, 18, ARRAY['XS','S','M','L','XL','XXL']);
