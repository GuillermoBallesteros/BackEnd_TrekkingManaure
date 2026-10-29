-- HU-N15 (RN-24): avisos por correo con Brevo. Cada fila es un aviso ya reclamado para una inscripcion; la
-- restriccion unica (inscripcion, tipo, referencia) impide enviarlo dos veces aunque la tarea corra de nuevo o haya
-- dos instancias. La idempotencyKey de Brevo dura 30 minutos y no basta. No guarda correos ni nombres.
CREATE TABLE envio_aviso (
    id             BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    inscripcion_id BIGINT       NOT NULL REFERENCES inscripcion (id) ON DELETE CASCADE,
    tipo           VARCHAR(20)  NOT NULL,
    -- Fecha compromiso (2026-11-09), codigo del pago (PAG-0007) o vispera y rol (2026-11-14/TITULAR).
    referencia     VARCHAR(40)  NOT NULL,
    estado         VARCHAR(10)  NOT NULL,
    intentos       INT          NOT NULL DEFAULT 1,
    mensaje_id     VARCHAR(120),
    creado_en      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_envio_aviso UNIQUE (inscripcion_id, tipo, referencia),
    CONSTRAINT ck_envio_aviso_tipo CHECK (tipo IN ('PAGO_3_DIAS', 'PAGO_1_DIA', 'PAGO_HOY', 'PAGO_VENCIDO',
        'PAGO_APROBADO', 'PAGO_RECHAZADO', 'EVENTO_MANANA')),
    CONSTRAINT ck_envio_aviso_estado CHECK (estado IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
    CONSTRAINT ck_envio_aviso_intentos CHECK (intentos >= 1)
);
