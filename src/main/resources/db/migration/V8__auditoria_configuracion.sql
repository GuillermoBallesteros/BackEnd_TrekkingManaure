ALTER TABLE configuracion_evento
    ADD COLUMN actualizado_por BIGINT,
    ADD COLUMN actualizado_en  TIMESTAMPTZ;

ALTER TABLE configuracion_evento
    ADD CONSTRAINT fk_config_actualizado_por FOREIGN KEY (actualizado_por) REFERENCES administrador (id);

CREATE INDEX ix_config_actualizado_por ON configuracion_evento (actualizado_por);
