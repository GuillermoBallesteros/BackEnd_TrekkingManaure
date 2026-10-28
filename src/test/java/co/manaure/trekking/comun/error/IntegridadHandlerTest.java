package co.manaure.trekking.comun.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
class IntegridadHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	private static DataIntegrityViolationException violacion(String restriccion) {
		var cause = new ConstraintViolationException(
				"duplicate key Key (documento_activo)=(DOC_SECRETO_999)",
				new SQLException("Key (documento_activo)=(DOC_SECRETO_999)"), restriccion);
		return new DataIntegrityViolationException("Key (documento_activo)=(DOC_SECRETO_999)", cause);
	}

	@Test
	void indiceDeDocumentoActivoSeTraduceADocumentoDuplicado409(CapturedOutput salida) {
		ResponseEntity<Object> r = handler.integridad(violacion("uq_participante_documento_activo"),
				new MockHttpServletRequest("POST", "/api/v1/reservas"));

		assertThat(r.getStatusCode().value()).isEqualTo(409);
		ProblemDetail pd = (ProblemDetail) r.getBody();
		assertThat(pd.getProperties()).containsEntry("code", "DOCUMENTO_DUPLICADO");
		assertThat(pd.getDetail()).doesNotContain("DOC_SECRETO_999");
		assertThat(salida.getAll()).doesNotContain("DOC_SECRETO_999");
	}

	@Test
	void elAvisoAmpliadoSoloVaEnLaRutaPublica() {
		var publico = (ProblemDetail) handler.integridad(violacion("uq_participante_documento_activo"),
				new MockHttpServletRequest("POST", "/api/v1/reservas")).getBody();
		var panel = (ProblemDetail) handler.integridad(violacion("uq_participante_documento_activo"),
				new MockHttpServletRequest("PUT", "/api/v1/admin/pagos/PAG-0001/revision")).getBody();
		assertThat(publico.getDetail()).isEqualTo("El documento ya tiene una reserva registrada. Si no fuiste tú, "
				+ "comunícate con la organización para que la revisen.");
		assertThat(panel.getDetail()).isEqualTo("El documento ya tiene una reserva registrada.");
	}

	@Test
	void otraRestriccionResponde500SinRegistrarValores(CapturedOutput salida) {
		ResponseEntity<Object> r = handler.integridad(violacion("ck_otra"), new MockHttpServletRequest("POST", "/api/v1/reservas"));

		assertThat(r.getStatusCode().value()).isEqualTo(500);
		assertThat(((ProblemDetail) r.getBody()).getProperties()).containsEntry("code", "ERROR_INTERNO");
		assertThat(salida.getAll()).contains("ck_otra").doesNotContain("DOC_SECRETO_999");
	}

}
