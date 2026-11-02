package co.manaure.trekking.auditoria;

/** Acciones auditables (lista cerrada por CHECK en la base, diseno §3.6). */
public enum AccionAuditoria {
	INICIAR_SESION, CERRAR_SESION, SESION_REUTILIZADA, BLOQUEO_CUENTA, APROBAR_PAGO, RECHAZAR_PAGO,
	/** Ya no se produce (RN-14: la aprobación es definitiva y no hay vuelta a revisión); se conserva por el historial. */
	DEVOLVER_PAGO_A_REVISION,
	REGISTRAR_PAGO_MANUAL, FIJAR_AUTORIZACION_MENOR, FIJAR_CAPACIDAD, FIJAR_FECHA_CIERRE,
	/** RN-25 y RN-26: ampliar el plazo o rechazar una reserva vencida. */
	AMPLIAR_PLAZO, RECHAZAR_RESERVA,
	/** Acciones del sistema (sin administrador): caducidad de pagos y alerta por exceso de solicitudes por IP. */
	RECHAZO_AUTOMATICO, ALERTA_RESERVAS_IP
}
