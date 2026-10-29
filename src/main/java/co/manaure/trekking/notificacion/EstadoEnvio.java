package co.manaure.trekking.notificacion;

/**
 * Estado de un aviso reclamado. PENDIENTE: reclamado y en curso (si la API cae ahí, no se repite). ENVIADO: Brevo lo
 * aceptó. FALLIDO: Brevo lo rechazó; se puede volver a reclamar hasta {@link RegistroEnvios#MAX_INTENTOS} veces.
 */
public enum EstadoEnvio { PENDIENTE, ENVIADO, FALLIDO }
