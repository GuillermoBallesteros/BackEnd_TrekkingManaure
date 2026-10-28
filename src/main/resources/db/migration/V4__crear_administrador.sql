CREATE TABLE administrador (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario    VARCHAR(50)  NOT NULL,
    clave_hash VARCHAR(100) NOT NULL,
    activo     BOOLEAN      NOT NULL DEFAULT true,
    creado_en  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_administrador_usuario UNIQUE (usuario)
);
