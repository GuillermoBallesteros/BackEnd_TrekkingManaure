package co.manaure.trekking.notificacion;

/** Quien recibe un aviso: nombre ya formateado (mayúscula inicial) y correo. Nunca se registra en logs. */
public record Destinatario(String nombre, String correo) {

	@Override
	public String toString() {
		return "Destinatario[***]";
	}

}
