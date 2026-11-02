-- RN-14, RN-25, RN-26: la aprobacion de un pago es definitiva; una reserva Vencida puede ampliar su plazo o
-- rechazarse para liberar sus cupos. Nuevos avisos por correo: reserva recibida, plazo ampliado y reserva rechazada.

-- 1) inscripcion.rechazada_en: momento en que la encargada rechazo la reserva vencida. La reserva conserva sus pagos
--    aprobados (son definitivos) pero queda Rechazada e inactiva, sin cupos ni documentos.
ALTER TABLE inscripcion ADD COLUMN rechazada_en TIMESTAMPTZ;
ALTER TABLE inscripcion ADD CONSTRAINT ck_inscripcion_rechazada_inactiva
    CHECK (rechazada_en IS NULL OR activa = FALSE);

-- 2) Acciones de auditoria nuevas.
ALTER TABLE auditoria DROP CONSTRAINT ck_auditoria_accion;
ALTER TABLE auditoria ADD CONSTRAINT ck_auditoria_accion CHECK (accion IN ('INICIAR_SESION', 'CERRAR_SESION',
    'SESION_REUTILIZADA', 'BLOQUEO_CUENTA', 'APROBAR_PAGO', 'RECHAZAR_PAGO', 'DEVOLVER_PAGO_A_REVISION',
    'REGISTRAR_PAGO_MANUAL', 'FIJAR_AUTORIZACION_MENOR', 'FIJAR_CAPACIDAD', 'FIJAR_FECHA_CIERRE',
    'RECHAZO_AUTOMATICO', 'ALERTA_RESERVAS_IP', 'AMPLIAR_PLAZO', 'RECHAZAR_RESERVA'));

-- 3) Tipos de aviso nuevos (los 7 anteriores no cambian).
ALTER TABLE envio_aviso DROP CONSTRAINT ck_envio_aviso_tipo;
ALTER TABLE envio_aviso ADD CONSTRAINT ck_envio_aviso_tipo CHECK (tipo IN ('PAGO_3_DIAS', 'PAGO_1_DIA', 'PAGO_HOY',
    'PAGO_VENCIDO', 'PAGO_APROBADO', 'PAGO_RECHAZADO', 'EVENTO_MANANA', 'RESERVA_RECIBIDA', 'PLAZO_AMPLIADO',
    'RESERVA_RECHAZADA'));
