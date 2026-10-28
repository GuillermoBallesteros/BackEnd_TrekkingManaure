-- Hallazgos H2/H3: los fallos de login se cuentan por pareja (IP, correo) y no por cuenta, para que un anonimo
-- no pueda bloquear al administrador. El correo se guarda como SHA-256 del correo normalizado (CHAR(64)):
-- el correo inexistente se cuenta igual que el existente y la tabla no almacena correos.
CREATE TABLE intento_login (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ip              VARCHAR(64) NOT NULL,
    correo_hash     CHAR(64)    NOT NULL,
    fallos          INT         NOT NULL DEFAULT 0,
    ultimo_fallo_en TIMESTAMPTZ NOT NULL,
    bloqueado_hasta TIMESTAMPTZ,
    CONSTRAINT uq_intento_login_par UNIQUE (ip, correo_hash),
    CONSTRAINT ck_intento_login_fallos CHECK (fallos >= 0)
);
CREATE INDEX ix_intento_login_correo ON intento_login (correo_hash, ultimo_fallo_en);
CREATE INDEX ix_intento_login_ultimo_fallo ON intento_login (ultimo_fallo_en);
