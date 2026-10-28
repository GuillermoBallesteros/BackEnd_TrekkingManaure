package co.manaure.trekking.seguridad.dto;

/** Refleja {@code Sesion} del panel; los tiempos son milisegundos desde epoch (diseño §6.3). */
public record SesionResponse(String token, String tipo, String correo, String nombre, long iniciadaEn,
		long expiraEn, long sesionExpiraEn, boolean recordada) {

	/** Sin el token en el texto. */
	@Override
	public String toString() {
		return "SesionResponse[recordada=" + recordada + "]";
	}

}
