-- HU-N01: personas, ficha de salud (detalles cifrados por la aplicacion), contacto y consentimiento.
CREATE TABLE persona (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombres          VARCHAR(60)  NOT NULL,
    apellidos        VARCHAR(60)  NOT NULL,
    tipo_documento   VARCHAR(20)  NOT NULL,
    documento        VARCHAR(20)  NOT NULL,
    fecha_nacimiento DATE         NOT NULL,
    genero           VARCHAR(30)  NOT NULL,
    telefono_pais    CHAR(2)      NOT NULL,
    telefono_numero  VARCHAR(15)  NOT NULL,
    correo           VARCHAR(254) NOT NULL,
    ciudad           VARCHAR(60)  NOT NULL,
    eps              VARCHAR(60)  NOT NULL,
    eps_otra         VARCHAR(60),
    creado_en        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_persona_tipo_documento FOREIGN KEY (tipo_documento) REFERENCES tipo_documento (clave),
    CONSTRAINT fk_persona_genero FOREIGN KEY (genero) REFERENCES genero (clave),
    CONSTRAINT fk_persona_pais FOREIGN KEY (telefono_pais) REFERENCES pais_telefono (iso),
    CONSTRAINT fk_persona_eps FOREIGN KEY (eps) REFERENCES eps (clave),
    CONSTRAINT ck_persona_documento CHECK (documento ~ '^[A-Z0-9]{5,20}$'),
    CONSTRAINT ck_persona_telefono CHECK (telefono_numero ~ '^[0-9]{7,15}$'),
    CONSTRAINT ck_persona_correo_minuscula CHECK (correo = lower(correo)),
    CONSTRAINT ck_persona_eps_otra CHECK ((eps = 'Otra') = (eps_otra IS NOT NULL))
);
CREATE INDEX ix_persona_documento ON persona (documento);
CREATE INDEX ix_persona_tipo_documento ON persona (tipo_documento);
CREATE INDEX ix_persona_genero ON persona (genero);
CREATE INDEX ix_persona_telefono_pais ON persona (telefono_pais);
CREATE INDEX ix_persona_eps ON persona (eps);

CREATE TABLE ficha_salud (
    persona_id           BIGINT  PRIMARY KEY,
    tiene_enfermedad     BOOLEAN NOT NULL,
    tiene_alergias       BOOLEAN NOT NULL,
    tiene_discapacidad   BOOLEAN NOT NULL,
    enfermedad_cifrada   TEXT,
    alergias_cifrada     TEXT,
    discapacidad_cifrada TEXT,
    practica_deporte     BOOLEAN NOT NULL,
    CONSTRAINT fk_ficha_salud_persona FOREIGN KEY (persona_id) REFERENCES persona (id),
    CONSTRAINT ck_ficha_salud_enfermedad CHECK (tiene_enfermedad = (enfermedad_cifrada IS NOT NULL)),
    CONSTRAINT ck_ficha_salud_alergias CHECK (tiene_alergias = (alergias_cifrada IS NOT NULL)),
    CONSTRAINT ck_ficha_salud_discapacidad CHECK (tiene_discapacidad = (discapacidad_cifrada IS NOT NULL))
);

CREATE TABLE contacto_emergencia (
    persona_id      BIGINT       PRIMARY KEY,
    nombre          VARCHAR(120) NOT NULL,
    telefono_pais   CHAR(2)      NOT NULL,
    telefono_numero VARCHAR(15)  NOT NULL,
    parentesco      VARCHAR(40)  NOT NULL,
    CONSTRAINT fk_contacto_persona FOREIGN KEY (persona_id) REFERENCES persona (id),
    CONSTRAINT fk_contacto_pais FOREIGN KEY (telefono_pais) REFERENCES pais_telefono (iso),
    CONSTRAINT fk_contacto_parentesco FOREIGN KEY (parentesco) REFERENCES parentesco (clave),
    CONSTRAINT ck_contacto_telefono CHECK (telefono_numero ~ '^[0-9]{7,15}$')
);
CREATE INDEX ix_contacto_telefono_pais ON contacto_emergencia (telefono_pais);
CREATE INDEX ix_contacto_parentesco ON contacto_emergencia (parentesco);

CREATE TABLE consentimiento_persona (
    persona_id                  BIGINT      PRIMARY KEY,
    version_terminos            VARCHAR(30) NOT NULL,
    aceptado_en                 TIMESTAMPTZ NOT NULL,
    uso_imagen                  BOOLEAN     NOT NULL,
    compromiso_areas_protegidas BOOLEAN     NOT NULL,
    compromiso_medio_ambiente   BOOLEAN     NOT NULL,
    CONSTRAINT fk_consentimiento_persona FOREIGN KEY (persona_id) REFERENCES persona (id),
    CONSTRAINT ck_consentimiento_areas_protegidas CHECK (compromiso_areas_protegidas = TRUE),
    CONSTRAINT ck_consentimiento_medio_ambiente CHECK (compromiso_medio_ambiente = TRUE)
);
