package co.manaure.trekking.auditoria;

/** Acciones auditables (lista cerrada por CHECK en la base, diseno §3.6). */
public enum AccionAuditoria {
	INICIAR_SESION, CERRAR_SESION, SESION_REUTILIZADA, BLOQUEO_CUENTA, APROBAR_PAGO, RECHAZAR_PAGO,
	DEVOLVER_PAGO_A_REVISION, REGISTRAR_PAGO_MANUAL, FIJAR_AUTORIZACION_MENOR, FIJAR_CAPACIDAD, FIJAR_FECHA_CIERRE,
	/** Acciones del sistema (sin administrador): caducidad de pagos y alerta por exceso de solicitudes por IP. */
	RECHAZO_AUTOMATICO, ALERTA_RESERVAS_IP
}
