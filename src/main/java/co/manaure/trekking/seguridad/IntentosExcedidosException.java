package co.manaure.trekking.seguridad;

import org.springframework.http.HttpStatus;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;

public class IntentosExcedidosException extends NegocioException {

	private final long reintentarEnSegundos;

	public IntentosExcedidosException(long reintentarEnSegundos) {
		super(CodigoError.INTENTOS_EXCEDIDOS, HttpStatus.TOO_MANY_REQUESTS,
				"Demasiados intentos. Intenta de nuevo más tarde.");
		this.reintentarEnSegundos = reintentarEnSegundos;
	}

	public long getReintentarEnSegundos() {
		return reintentarEnSegundos;
	}

}
