package co.manaure.trekking.comun.error;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import co.manaure.trekking.regla.ErrorRegla;

public class NegocioException extends RuntimeException {

	private final CodigoError code;
	private final HttpStatus status;
	private final Map<String, Object> props;

	public NegocioException(CodigoError code, HttpStatus status, String detail) {
		this(code, status, detail, Map.of());
	}

	public NegocioException(CodigoError code, HttpStatus status, String detail, Map<String, Object> props) {
		super(detail);
		this.code = code;
		this.status = status;
		this.props = new LinkedHashMap<>(props);
	}

	/**
	 * Error de una regla de negocio como respuesta HTTP (diseño §12.1): 409 para cierre, documento duplicado y
	 * cupo; 422 para el resto. El campo con error viaja en {@code campo}.
	 */
	public static NegocioException de(ErrorRegla error) {
		HttpStatus estado = switch (error.code()) {
			case INSCRIPCIONES_CERRADAS, DOCUMENTO_DUPLICADO, SIN_CUPO_PARAMO, CARRERA_AGOTADA -> HttpStatus.CONFLICT;
			default -> HttpStatus.UNPROCESSABLE_ENTITY;
		};
		return new NegocioException(error.code(), estado, error.detail(),
				error.campo() == null ? Map.of() : Map.of("campo", error.campo()));
	}

	public CodigoError getCode() { return code; }
	public HttpStatus getStatus() { return status; }
	public Map<String, Object> getProps() { return props; }

}
