ALTER TABLE inscripcion
    ADD COLUMN anulacion_motivo VARCHAR(200),
    ADD COLUMN anulada_en       TIMESTAMPTZ,
    ADD COLUMN anulada_por      BIGINT;

ALTER TABLE inscripcion
    ADD CONSTRAINT fk_inscripcion_anulada_por FOREIGN KEY (anulada_por) REFERENCES administrador (id);

ALTER TABLE inscripcion
    ADD CONSTRAINT ck_inscripcion_anulacion CHECK ((estado = 'ANULADO') = (anulada_en IS NOT NULL
        AND anulada_por IS NOT NULL AND anulacion_motivo IS NOT NULL));

CREATE INDEX ix_inscripcion_anulada_por ON inscripcion (anulada_por);
