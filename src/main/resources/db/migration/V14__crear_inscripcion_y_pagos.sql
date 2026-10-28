-- HU-N01: inscripcion, participantes, extras, hotel previo y pagos. Codigos PER-/PAG- por secuencia.
CREATE SEQUENCE inscripcion_codigo_seq START 1;
CREATE SEQUENCE pago_codigo_seq START 1;

CREATE TABLE inscripcion (
    id                                  BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo                              VARCHAR(12) NOT NULL,
    plan_pago                           VARCHAR(10) NOT NULL,
    fecha_compromiso                    DATE,
    autorizacion_menor                  BOOLEAN     NOT NULL DEFAULT FALSE,
    activa                              BOOLEAN     NOT NULL DEFAULT TRUE,
    precio_titular_aplicado             BIGINT      NOT NULL,
    precio_apartado_aplicado            BIGINT      NOT NULL,
    precio_acompanante_sin_kit_aplicado BIGINT      NOT NULL,
    precio_acompanante_con_kit_aplicado BIGINT      NOT NULL,
    precio_cuatrimoto_aplicado          BIGINT      NOT NULL,
    precio_parapente_aplicado           BIGINT      NOT NULL,
    precio_hotel_noche_aplicado         BIGINT      NOT NULL,
    total                               BIGINT      NOT NULL,
    fecha_registro                      DATE        NOT NULL,
    creado_en                           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_inscripcion_codigo UNIQUE (codigo),
    CONSTRAINT ck_inscripcion_codigo CHECK (codigo ~ '^PER-[0-9]{4,}$'),
    CONSTRAINT ck_inscripcion_plan CHECK (plan_pago IN ('TOTAL', 'APARTADO')),
    CONSTRAINT ck_inscripcion_fecha_compromiso CHECK ((plan_pago = 'APARTADO') = (fecha_compromiso IS NOT NULL)),
    CONSTRAINT ck_inscripcion_precios_aplicados CHECK (precio_titular_aplicado >= 0 AND precio_apartado_aplicado >= 0
        AND precio_acompanante_sin_kit_aplicado >= 0 AND precio_acompanante_con_kit_aplicado >= 0
        AND precio_cuatrimoto_aplicado >= 0 AND precio_parapente_aplicado >= 0 AND precio_hotel_noche_aplicado >= 0),
    CONSTRAINT ck_inscripcion_total CHECK (total > 0)
);
CREATE INDEX ix_inscripcion_activa ON inscripcion (activa);
CREATE INDEX ix_inscripcion_fecha_registro ON inscripcion (fecha_registro DESC);

CREATE TABLE inscripcion_participante (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inscripcion_id   BIGINT      NOT NULL,
    persona_id       BIGINT      NOT NULL,
    rol              VARCHAR(12) NOT NULL,
    estadia          VARCHAR(30) NOT NULL,
    lleva_kit        BOOLEAN     NOT NULL,
    talla            VARCHAR(5),
    documento_activo VARCHAR(20),
    CONSTRAINT fk_participante_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES inscripcion (id),
    CONSTRAINT fk_participante_persona FOREIGN KEY (persona_id) REFERENCES persona (id),
    CONSTRAINT fk_participante_estadia FOREIGN KEY (estadia) REFERENCES estadia (clave),
    CONSTRAINT fk_participante_talla FOREIGN KEY (talla) REFERENCES talla (clave),
    CONSTRAINT uq_participante_inscripcion_rol UNIQUE (inscripcion_id, rol),
    CONSTRAINT uq_participante_persona UNIQUE (persona_id),
    CONSTRAINT ck_participante_rol CHECK (rol IN ('TITULAR', 'ACOMPANANTE')),
    CONSTRAINT ck_participante_titular_kit CHECK (rol <> 'TITULAR' OR lleva_kit),
    CONSTRAINT ck_participante_talla CHECK (lleva_kit = (talla IS NOT NULL))
);
CREATE INDEX ix_participante_inscripcion ON inscripcion_participante (inscripcion_id);
CREATE INDEX ix_participante_estadia ON inscripcion_participante (estadia);
CREATE INDEX ix_participante_talla ON inscripcion_participante (talla);
CREATE UNIQUE INDEX uq_participante_documento_activo ON inscripcion_participante (documento_activo)
    WHERE documento_activo IS NOT NULL;

CREATE TABLE inscripcion_extra (
    inscripcion_id BIGINT      NOT NULL,
    extra          VARCHAR(30) NOT NULL,
    PRIMARY KEY (inscripcion_id, extra),
    CONSTRAINT fk_inscripcion_extra_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES inscripcion (id),
    CONSTRAINT fk_inscripcion_extra_extra FOREIGN KEY (extra) REFERENCES extra (clave)
);
CREATE INDEX ix_inscripcion_extra_extra ON inscripcion_extra (extra);

CREATE TABLE hotel_previo (
    inscripcion_id  BIGINT  PRIMARY KEY,
    entrada         DATE    NOT NULL,
    salida          DATE    NOT NULL,
    con_acompanante BOOLEAN NOT NULL,
    CONSTRAINT fk_hotel_previo_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES inscripcion (id),
    CONSTRAINT ck_hotel_previo_fechas CHECK (salida > entrada)
);

CREATE TABLE pago (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo         VARCHAR(12)  NOT NULL,
    inscripcion_id BIGINT       NOT NULL,
    concepto       VARCHAR(20)  NOT NULL,
    monto          BIGINT       NOT NULL,
    fecha          DATE         NOT NULL,
    revision       VARCHAR(12)  NOT NULL,
    motivo_rechazo VARCHAR(200),
    origen         VARCHAR(10)  NOT NULL,
    registrado_por BIGINT,
    revisado_por   BIGINT,
    revisado_en    TIMESTAMPTZ,
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_pago_codigo UNIQUE (codigo),
    CONSTRAINT fk_pago_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES inscripcion (id),
    CONSTRAINT fk_pago_registrado_por FOREIGN KEY (registrado_por) REFERENCES administrador (id),
    CONSTRAINT fk_pago_revisado_por FOREIGN KEY (revisado_por) REFERENCES administrador (id),
    CONSTRAINT ck_pago_codigo CHECK (codigo ~ '^PAG-[0-9]{4,}$'),
    CONSTRAINT ck_pago_concepto CHECK (concepto IN ('SEPARACION_CUPO', 'PAGO_TOTAL', 'PAGO_SALDO')),
    CONSTRAINT ck_pago_monto CHECK (monto > 0),
    CONSTRAINT ck_pago_revision CHECK (revision IN ('POR_REVISAR', 'APROBADO', 'RECHAZADO')),
    CONSTRAINT ck_pago_motivo CHECK ((revision = 'RECHAZADO') = (motivo_rechazo IS NOT NULL)),
    CONSTRAINT ck_pago_origen CHECK (origen IN ('PUBLICO', 'MANUAL')),
    CONSTRAINT ck_pago_registrado_por CHECK ((origen = 'MANUAL') = (registrado_por IS NOT NULL)),
    CONSTRAINT ck_pago_revisado CHECK ((revision = 'POR_REVISAR') = (revisado_en IS NULL AND revisado_por IS NULL))
);
CREATE INDEX ix_pago_inscripcion ON pago (inscripcion_id);
CREATE INDEX ix_pago_registrado_por ON pago (registrado_por);
CREATE INDEX ix_pago_revisado_por ON pago (revisado_por);
CREATE INDEX ix_pago_inscripcion_aprobado ON pago (inscripcion_id) WHERE revision = 'APROBADO';
