-- HU-N01: configuracion del evento, una sola fila (id = 1). Valores iniciales de shared/config del front.
CREATE TABLE configuracion_evento (
    id                         SMALLINT     PRIMARY KEY,
    nombre_evento              VARCHAR(150) NOT NULL,
    fecha_evento_inicio        DATE         NOT NULL,
    fecha_evento_fin           DATE         NOT NULL,
    fecha_apertura             DATE         NOT NULL,
    fecha_cierre               DATE         NOT NULL,
    cupos_paramo               INT          NOT NULL,
    precio_titular             BIGINT       NOT NULL,
    valor_apartado             BIGINT       NOT NULL,
    precio_acompanante_sin_kit BIGINT       NOT NULL,
    precio_acompanante_con_kit BIGINT       NOT NULL,
    precio_cuatrimoto          BIGINT       NOT NULL,
    precio_parapente           BIGINT       NOT NULL,
    precio_hotel_noche         BIGINT       NOT NULL,
    version_terminos           VARCHAR(30)  NOT NULL,
    actualizado_por            BIGINT,
    actualizado_en             TIMESTAMPTZ,
    CONSTRAINT ck_configuracion_unica CHECK (id = 1),
    CONSTRAINT ck_configuracion_fechas_evento CHECK (fecha_evento_inicio <= fecha_evento_fin),
    CONSTRAINT ck_configuracion_apertura CHECK (fecha_apertura < fecha_evento_inicio),
    CONSTRAINT ck_configuracion_cierre CHECK (fecha_cierre >= fecha_apertura AND fecha_cierre < fecha_evento_inicio),
    CONSTRAINT ck_configuracion_cupos CHECK (cupos_paramo >= 1),
    CONSTRAINT ck_configuracion_precio_titular CHECK (precio_titular > 0),
    CONSTRAINT ck_configuracion_apartado CHECK (valor_apartado > 0 AND valor_apartado <= precio_titular),
    CONSTRAINT ck_configuracion_precios CHECK (precio_acompanante_sin_kit >= 0 AND precio_acompanante_con_kit >= 0
        AND precio_cuatrimoto >= 0 AND precio_parapente >= 0 AND precio_hotel_noche >= 0),
    CONSTRAINT fk_configuracion_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES administrador (id)
);
CREATE INDEX ix_configuracion_actualizado_por ON configuracion_evento (actualizado_por);

INSERT INTO configuracion_evento (id, nombre_evento, fecha_evento_inicio, fecha_evento_fin, fecha_apertura, fecha_cierre,
    cupos_paramo, precio_titular, valor_apartado, precio_acompanante_sin_kit, precio_acompanante_con_kit,
    precio_cuatrimoto, precio_parapente, precio_hotel_noche, version_terminos)
VALUES (1, 'Trekking Desafío Perijá 13K', DATE '2026-11-15', DATE '2026-11-16', DATE '2026-10-28', DATE '2026-11-09',
    60, 350000, 50000, 150000, 350000, 150000, 200000, 45000, 'TYC-2026-10-28');
