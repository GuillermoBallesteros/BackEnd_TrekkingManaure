package co.manaure.trekking.comun.error;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import co.manaure.trekking.regla.Textos;
import co.manaure.trekking.seguridad.CuerpoDemasiadoGrandeException;
import jakarta.servlet.http.HttpServletRequest;
import co.manaure.trekking.seguridad.IntentosExcedidosException;

/**
 * Traduce excepciones a ProblemDetail con campo `code`. Nunca registra ni devuelve valores recibidos
 * ni el mensaje de HttpMessageNotReadableException (RN-19).
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** Diseño §12.1: 400 DATOS_INVALIDOS. */
	private static final String DETALLE_DATOS_INVALIDOS =
			"Los datos enviados están incompletos o tienen un formato inválido.";

	@ExceptionHandler(NegocioException.class)
	ResponseEntity<Object> negocio(NegocioException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		pd.setProperty("code", ex.getCode().name());
		ex.getProps().forEach(pd::setProperty);
		return ResponseEntity.status(ex.getStatus()).body(pd);
	}

	/** Cuerpo por encima del tope (lo corta LimiteCuerpoFilter): 413 sin eco de lo recibido. */
	@ExceptionHandler(CuerpoDemasiadoGrandeException.class)
	ResponseEntity<Object> cuerpoDemasiadoGrande(CuerpoDemasiadoGrandeException ex) {
		return respuestaCuerpoDemasiadoGrande();
	}

	private static ResponseEntity<Object> respuestaCuerpoDemasiadoGrande() {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "La solicitud es demasiado grande.");
		pd.setProperty("code", CodigoError.CUERPO_DEMASIADO_GRANDE.name());
		return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(pd);
	}

	@ExceptionHandler(IntentosExcedidosException.class)
	ResponseEntity<Object> intentosExcedidos(IntentosExcedidosException ex) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		pd.setProperty("code", ex.getCode().name());
		return ResponseEntity.status(ex.getStatus())
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getReintentarEnSegundos())).body(pd);
	}

	/** Traduce por nombre de restricción. Nunca registra el mensaje (trae Key(...)=(valor)). */
	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> integridad(DataIntegrityViolationException ex, HttpServletRequest request) {
		String restriccion = nombreRestriccion(ex);
		if ("uq_participante_documento_activo".equals(restriccion)) {
			// Audiencias distintas: el público recibe el aviso ampliado; el panel, el mensaje original.
			boolean panel = request.getRequestURI() != null && request.getRequestURI().startsWith("/api/v1/admin/");
			ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
					"El documento ya tiene una reserva registrada." + (panel ? "" : Textos.AYUDA_DOCUMENTO_AJENO));
			pd.setProperty("code", CodigoError.DOCUMENTO_DUPLICADO.name());
			return ResponseEntity.status(HttpStatus.CONFLICT).body(pd);
		}
		log.error("Violación de integridad: {} restriccion={}", ex.getClass().getName(), restriccion);
		return inesperadoSinLog();
	}

	private static String nombreRestriccion(Throwable ex) {
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
				String n = cve.getConstraintName();
				int punto = n.lastIndexOf('.');
				return punto >= 0 ? n.substring(punto + 1) : n;
			}
		}
		// Respaldo: busca el nombre conocido en el mensaje sin registrarlo ni devolverlo.
		for (Throwable t = ex; t != null; t = t.getCause()) {
			if (t.getMessage() != null) {
				for (String conocida : List.of("uq_participante_documento_activo")) {
					if (t.getMessage().contains(conocida)) {
						return conocida;
					}
				}
			}
		}
		return null;
	}

	private ResponseEntity<Object> inesperadoSinLog() {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Ocurrió un error interno. Intenta de nuevo más tarde.");
		pd.setProperty("code", CodigoError.ERROR_INTERNO.name());
		return ResponseEntity.internalServerError().body(pd);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> inesperado(Exception ex) {
		log.error("Error inesperado: {}", ex.getClass().getName());
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Ocurrió un error interno. Intenta de nuevo más tarde.");
		pd.setProperty("code", CodigoError.ERROR_INTERNO.name());
		return ResponseEntity.internalServerError().body(pd);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, DETALLE_DATOS_INVALIDOS);
		pd.setProperty("code", CodigoError.DATOS_INVALIDOS.name());
		ex.getBindingResult().getFieldErrors().stream().findFirst()
				.ifPresent(e -> pd.setProperty("campo", e.getField()));
		List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
				.map(e -> Map.of("campo", e.getField(), "mensaje", String.valueOf(e.getDefaultMessage())))
				.toList();
		pd.setProperty("errores", errores);
		return ResponseEntity.badRequest().body(pd);
	}

	/** Parámetro con tipo/valor inválido (p. ej. estado u orden fuera de lista): 400 sin eco del valor. */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Un parámetro de la solicitud tiene un valor no permitido.");
		pd.setProperty("code", CodigoError.DATOS_INVALIDOS.name());
		if (ex.getPropertyName() != null) {
			pd.setProperty("errores", List.of(Map.of("campo", ex.getPropertyName(), "mensaje", "Valor no permitido.")));
		}
		return ResponseEntity.badRequest().body(pd);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		if (CuerpoDemasiadoGrandeException.enCadena(ex)) {
			return respuestaCuerpoDemasiadoGrande();
		}
		ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				DETALLE_DATOS_INVALIDOS);
		pd.setProperty("code", CodigoError.DATOS_INVALIDOS.name());
		return ResponseEntity.badRequest().body(pd);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> r = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (r != null && r.getBody() instanceof ProblemDetail pd
				&& (pd.getProperties() == null || !pd.getProperties().containsKey("code"))) {
			CodigoError code = statusCode.value() == 404 ? CodigoError.RECURSO_NO_ENCONTRADO
					: statusCode.is4xxClientError() ? CodigoError.DATOS_INVALIDOS : CodigoError.ERROR_INTERNO;
			pd.setProperty("code", code.name());
		}
		return r;
	}

}
