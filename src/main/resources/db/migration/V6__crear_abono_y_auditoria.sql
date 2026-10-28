CREATE TABLE abono (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inscripcion_id   BIGINT       NOT NULL,
    monto            BIGINT       NOT NULL,
    fecha_pago       DATE         NOT NULL,
    referencia_nequi VARCHAR(60),
    nota             VARCHAR(300),
    registrado_por   BIGINT       NOT NULL,
    registrado_en    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    anulado          BOOLEAN      NOT NULL DEFAULT false,
    anulacion_motivo VARCHAR(200),
    anulado_por      BIGINT,
    anulado_en       TIMESTAMPTZ,
    CONSTRAINT fk_abono_inscripcion FOREIGN KEY (inscripcion_id) REFERENCES inscripcion (id),
    CONSTRAINT fk_abono_registrado_por FOREIGN KEY (registrado_por) REFERENCES administrador (id),
    CONSTRAINT fk_abono_anulado_por FOREIGN KEY (anulado_por) REFERENCES administrador (id),
    CONSTRAINT ck_abono_monto_positivo CHECK (monto > 0),
    CONSTRAINT ck_abono_anulacion CHECK (anulado = (anulado_en IS NOT NULL AND anulado_por IS NOT NULL
                                                    AND anulacion_motivo IS NOT NULL))
);

CREATE INDEX ix_abono_inscripcion ON abono (inscripcion_id);
CREATE INDEX ix_abono_registrado_por ON abono (registrado_por);
CREATE INDEX ix_abono_anulado_por ON abono (anulado_por);
CREATE UNIQUE INDEX uq_abono_referencia_vigente ON abono (referencia_nequi)
    WHERE NOT anulado AND referencia_nequi IS NOT NULL;

CREATE TABLE auditoria (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    administrador_id BIGINT       NOT NULL,
    accion           VARCHAR(30)  NOT NULL,
    entidad          VARCHAR(30)  NOT NULL,
    entidad_ref      VARCHAR(40)  NOT NULL,
    campos           VARCHAR(60)[],
    fecha            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_auditoria_administrador FOREIGN KEY (administrador_id) REFERENCES administrador (id),
    CONSTRAINT ck_auditoria_accion CHECK (accion IN ('REGISTRAR_ABONO', 'ANULAR_ABONO', 'ANULAR_INSCRIPCION',
                                                     'CORREGIR_PERSONA', 'EDITAR_CONFIGURACION'))
);

CREATE INDEX ix_auditoria_administrador ON auditoria (administrador_id);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_ref);
