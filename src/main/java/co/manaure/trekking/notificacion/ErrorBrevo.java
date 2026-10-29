package co.manaure.trekking.notificacion;

/**
 * Brevo no aceptó el correo. {@code estado} es el HTTP de la respuesta, o 0 si no hubo respuesta (tiempo agotado o
 * red). El mensaje nunca incluye el cuerpo enviado ni datos de la persona.
 */
public class ErrorBrevo extends RuntimeException {

	private final int estado;

	public ErrorBrevo(int estado, Throwable causa) {
		super(estado == 0 ? "Brevo no respondió." : "Brevo respondió " + estado + ".", causa);
		this.estado = estado;
	}

	public int estado() {
		return estado;
	}

}
