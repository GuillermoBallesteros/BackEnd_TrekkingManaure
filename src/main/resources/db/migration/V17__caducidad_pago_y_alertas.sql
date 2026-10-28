-- H4: caducidad a 12 horas de los pagos sin verificar y alerta por exceso de solicitudes por IP.

-- 1) pago.en_revision_desde: desde cuando el pago esta en "Por revisar" (creacion, o la ultima vez que la
--    encargada lo devolvio a "Por revisar"). La caducidad automatica se mide desde aqui, no desde la reserva.
ALTER TABLE pago ADD COLUMN en_revision_desde TIMESTAMPTZ;
UPDATE pago SET en_revision_desde = creado_en;
ALTER TABLE pago ALTER COLUMN en_revision_desde SET DEFAULT now();
ALTER TABLE pago ALTER COLUMN en_revision_desde SET NOT NULL;
CREATE INDEX ix_pago_por_revisar_desde ON pago (en_revision_desde) WHERE revision = 'POR_REVISAR';

-- 2) Acciones del sistema en auditoria: RECHAZO_AUTOMATICO (tarea de caducidad) y ALERTA_RESERVAS_IP (tope por IP).
--    Las acciones del sistema no tienen administrador: auditoria.administrador_id ya admite NULL (V15) y
--    pago.revisado_por tambien; ck_pago_revisado solo exige revisado_en para un pago revisado, de modo que un rechazo
--    automatico (revisado_por NULL, revisado_en = ahora) lo cumple sin cambiar esas restricciones.
ALTER TABLE auditoria DROP CONSTRAINT ck_auditoria_accion;
ALTER TABLE auditoria ADD CONSTRAINT ck_auditoria_accion CHECK (accion IN ('INICIAR_SESION', 'CERRAR_SESION',
    'SESION_REUTILIZADA', 'BLOQUEO_CUENTA', 'APROBAR_PAGO', 'RECHAZAR_PAGO', 'DEVOLVER_PAGO_A_REVISION',
    'REGISTRAR_PAGO_MANUAL', 'FIJAR_AUTORIZACION_MENOR', 'FIJAR_CAPACIDAD', 'FIJAR_FECHA_CIERRE',
    'RECHAZO_AUTOMATICO', 'ALERTA_RESERVAS_IP'));
