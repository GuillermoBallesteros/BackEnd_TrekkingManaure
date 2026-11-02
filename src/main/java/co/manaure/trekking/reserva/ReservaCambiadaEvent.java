package co.manaure.trekking.reserva;

/**
 * Una reserva cambió sin que cambiara la revisión de un pago: se creó, se amplió su plazo (RN-25) o se rechazó por
 * vencida (RN-26). Se publica dentro de la transacción; los avisos por correo (RN-24) lo atienden solo después del
 * commit. {@code referencia} hace único el aviso dentro de la inscripción.
 */
public record ReservaCambiadaEvent(String codigoReserva, Cambio cambio, String referencia) {

	public enum Cambio {
		RECIBIDA, PLAZO_AMPLIADO, RECHAZADA
	}

}
