CREATE SEQUENCE inscripcion_codigo_seq START 1;

CREATE TABLE inscripcion (
    id                                   BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    codigo                               VARCHAR(12)  NOT NULL,
    estado                               VARCHAR(20)  NOT NULL,
    modalidad_pago                       VARCHAR(10)  NOT NULL,
    fecha_compromiso                     DATE,
    lleva_acompanante                    BOOLEAN      NOT NULL,
    precio_con_kit_aplicado              BIGINT       NOT NULL,
    precio_acompanante_sin_kit_aplicado  BIGINT       NOT NULL,
    precio_cuatritour_aplicado           BIGINT       NOT NULL,
    precio_parapente_aplicado            BIGINT       NOT NULL,
    valor_reserva_aplicado               BIGINT       NOT NULL,
    total                                BIGINT       NOT NULL,
    abonado                              BIGINT       NOT NULL DEFAULT 0,
    acepta_tratamiento_datos             BOOLEAN      NOT NULL,
    acepta_tratamiento_en                TIMESTAMPTZ  NOT NULL,
    fecha_registro                       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_inscripcion_codigo UNIQUE (codigo),
    CONSTRAINT ck_inscripcion_codigo CHECK (codigo ~ '^TM-[0-9]{6,}$'),
    CONSTRAINT ck_inscripcion_estado CHECK (estado IN ('PENDIENTE', 'SALDO_PENDIENTE', 'PAGADO', 'ANULADO')),
    CONSTRAINT ck_inscripcion_modalidad CHECK (modalidad_pago IN ('TOTAL', 'RESERVA')),
    CONSTRAINT ck_inscripcion_fecha_compromiso CHECK ((modalidad_pago = 'RESERVA') = (fecha_compromiso IS NOT NULL)),
    CONSTRAINT ck_inscripcion_precios CHECK (precio_con_kit_aplicado >= 0 AND precio_acompanante_sin_kit_aplicado >= 0
        AND precio_cuatritour_aplicado >= 0 AND precio_parapente_aplicado >= 0 AND valor_reserva_aplicado >= 0),
    CONSTRAINT ck_inscripcion_total CHECK (total > 0),
    CONSTRAINT ck_inscripcion_abonado CHECK (abonado >= 0),
    CONSTRAINT ck_inscripcion_consentimiento CHECK (acepta_tratamiento_datos = true)
);

CREATE INDEX ix_inscripcion_estado ON inscripcion (estado);
CREATE INDEX ix_inscripcion_fecha_registro ON inscripcion (fecha_registro DESC);
CREATE INDEX ix_inscripcion_fecha_compromiso ON inscripcion (fecha_compromiso);

CREATE TABLE persona (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inscripcion_id   BIGINT       NOT NULL REFERENCES inscripcion (id),
    rol              VARCHAR(12)  NOT NULL,
    nombres          VARCHAR(80)  NOT NULL,
    apellidos        VARCHAR(80)  NOT NULL,
    tipo_documento   VARCHAR(3)   NOT NULL,
    numero_documento VARCHAR(20)  NOT NULL,
    telefono         VARCHAR(16)  NOT NULL,
    correo           VARCHAR(120) NOT NULL,
    fecha_nacimiento DATE         NOT NULL,
    genero           VARCHAR(10)  NOT NULL,
    lleva_kit        BOOLEAN      NOT NULL,
    talla            VARCHAR(5),
    hospedaje        VARCHAR(15)  NOT NULL,
    cuatritour       BOOLEAN      NOT NULL,
    parapente        BOOLEAN      NOT NULL,
    vigente          BOOLEAN      NOT NULL DEFAULT true,
    CONSTRAINT uq_persona_inscripcion_rol UNIQUE (inscripcion_id, rol),
    CONSTRAINT ck_persona_rol CHECK (rol IN ('TITULAR', 'ACOMPANANTE')),
    CONSTRAINT ck_persona_nombres CHECK (length(nombres) >= 1 AND length(apellidos) >= 1),
    CONSTRAINT ck_persona_tipo_documento CHECK (tipo_documento IN ('CC', 'TI', 'CE', 'PAS')),
    CONSTRAINT ck_persona_numero_documento CHECK (numero_documento ~ '^[A-Za-z0-9]{5,20}$'),
    CONSTRAINT ck_persona_telefono CHECK (telefono ~ '^\+?[0-9]{7,15}$'),
    CONSTRAINT ck_persona_genero CHECK (genero IN ('MASCULINO', 'FEMENINO', 'OTRO')),
    CONSTRAINT ck_persona_titular_kit CHECK (rol <> 'TITULAR' OR lleva_kit),
    CONSTRAINT ck_persona_talla CHECK (lleva_kit = (talla IS NOT NULL)),
    CONSTRAINT ck_persona_hospedaje CHECK (hospedaje IN ('PARAMO', 'HOTEL_MANAURE'))
);

CREATE INDEX ix_persona_inscripcion ON persona (inscripcion_id);
CREATE INDEX ix_persona_vigente ON persona (vigente);
CREATE UNIQUE INDEX uq_persona_documento_vigente ON persona (numero_documento) WHERE vigente;

CREATE TABLE ficha_salud (
    persona_id          BIGINT       PRIMARY KEY REFERENCES persona (id),
    tiene_enfermedad    BOOLEAN      NOT NULL,
    enfermedad_detalle  VARCHAR(300),
    tiene_alergias      BOOLEAN      NOT NULL,
    alergias_detalle    VARCHAR(300),
    practica_deporte    BOOLEAN      NOT NULL,
    CONSTRAINT ck_ficha_enfermedad CHECK (tiene_enfermedad = (enfermedad_detalle IS NOT NULL)),
    CONSTRAINT ck_ficha_alergias CHECK (tiene_alergias = (alergias_detalle IS NOT NULL))
);
