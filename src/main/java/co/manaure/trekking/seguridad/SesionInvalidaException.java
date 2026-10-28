package co.manaure.trekking.seguridad;

import org.springframework.http.HttpStatus;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;

/** 401 SESION_INVALIDA (refresh ausente, inexistente, vencido, revocado, reutilizado o de cuenta inactiva). */
public class SesionInvalidaException extends NegocioException {

	public static final String DETALLE = "Tu sesión terminó. Inicia sesión de nuevo.";

	public SesionInvalidaException() {
		super(CodigoError.SESION_INVALIDA, HttpStatus.UNAUTHORIZED, DETALLE);
	}

}
