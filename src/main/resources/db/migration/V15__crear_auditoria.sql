-- HU-N01: auditoria de acciones de administracion. El detalle nunca lleva datos personales ni de salud.
CREATE TABLE auditoria (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    administrador_id BIGINT,
    accion           VARCHAR(40)  NOT NULL,
    entidad          VARCHAR(30)  NOT NULL,
    entidad_ref      VARCHAR(40)  NOT NULL,
    detalle          VARCHAR(300),
    fecha            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_auditoria_administrador FOREIGN KEY (administrador_id) REFERENCES administrador (id),
    CONSTRAINT ck_auditoria_accion CHECK (accion IN ('INICIAR_SESION', 'CERRAR_SESION', 'SESION_REUTILIZADA',
        'BLOQUEO_CUENTA', 'APROBAR_PAGO', 'RECHAZAR_PAGO', 'DEVOLVER_PAGO_A_REVISION', 'REGISTRAR_PAGO_MANUAL',
        'FIJAR_AUTORIZACION_MENOR', 'FIJAR_CAPACIDAD', 'FIJAR_FECHA_CIERRE')),
    CONSTRAINT ck_auditoria_entidad CHECK (entidad IN ('PAGO', 'RESERVA', 'CONFIGURACION', 'SESION'))
);
CREATE INDEX ix_auditoria_administrador ON auditoria (administrador_id);
CREATE INDEX ix_auditoria_entidad ON auditoria (entidad, entidad_ref);
