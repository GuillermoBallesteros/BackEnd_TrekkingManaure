package co.manaure.trekking.seguridad;

import java.io.IOException;

/** El cuerpo de la solicitud superó el tope. Nunca lleva datos de la solicitud. */
public class CuerpoDemasiadoGrandeException extends IOException {

	public CuerpoDemasiadoGrandeException() {
		super("Cuerpo demasiado grande");
	}

	/** Busca esta excepción en la cadena de causas. */
	public static boolean enCadena(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof CuerpoDemasiadoGrandeException) {
				return true;
			}
			if (t.getCause() == t) {
				break;
			}
		}
		return false;
	}

}
