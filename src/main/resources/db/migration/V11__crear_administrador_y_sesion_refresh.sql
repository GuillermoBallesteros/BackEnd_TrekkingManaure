-- HU-N01: administrador (correo unico) y sesiones de refresh (se usan desde HU-N05/N06).
CREATE TABLE administrador (
    id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correo            VARCHAR(254) NOT NULL,
    clave_hash        VARCHAR(100) NOT NULL,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    bloqueado_hasta   TIMESTAMPTZ,
    ultimo_acceso_en  TIMESTAMPTZ,
    creado_en         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_administrador_correo UNIQUE (correo),
    CONSTRAINT ck_administrador_correo_minuscula CHECK (correo = lower(correo)),
    CONSTRAINT ck_administrador_intentos CHECK (intentos_fallidos >= 0)
);

CREATE TABLE sesion_refresh (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    administrador_id BIGINT      NOT NULL,
    familia          UUID        NOT NULL,
    token_hash       CHAR(64)    NOT NULL,
    recordada        BOOLEAN     NOT NULL,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT now(),
    expira_en        TIMESTAMPTZ NOT NULL,
    usado_en         TIMESTAMPTZ,
    revocado_en      TIMESTAMPTZ,
    reemplazado_por  BIGINT,
    CONSTRAINT fk_sesion_refresh_administrador FOREIGN KEY (administrador_id) REFERENCES administrador (id),
    CONSTRAINT fk_sesion_refresh_reemplazado FOREIGN KEY (reemplazado_por) REFERENCES sesion_refresh (id),
    CONSTRAINT uq_sesion_refresh_token UNIQUE (token_hash)
);
CREATE INDEX ix_sesion_refresh_administrador ON sesion_refresh (administrador_id);
CREATE INDEX ix_sesion_refresh_familia ON sesion_refresh (familia);
CREATE INDEX ix_sesion_refresh_expira ON sesion_refresh (expira_en);
CREATE INDEX ix_sesion_refresh_reemplazado ON sesion_refresh (reemplazado_por);
