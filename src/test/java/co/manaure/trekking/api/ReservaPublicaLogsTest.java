package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** HU-N04 / RN-19: ni datos de salud ni datos personales en los logs, con éxito o con error. */
@ExtendWith(OutputCaptureExtension.class)
class ReservaPublicaLogsTest extends ReservaPublicaBase {

	private static final String[] SENSIBLES = { "Penicilina", "Asma leve", "Ana María", "Pérez", "Carlos", "Rojas",
			"1065123456", "1065999888", "ana@example.com", "carlos@example.com", "3001234567", "3109998877",
			"3157654321", "3201112233", "Valledupar", "Marta" };

	private static void sinDatosSensibles(CapturedOutput salida) {
		assertThat(salida.getAll()).doesNotContain(SENSIBLES);
	}

	@Test
	void registroExitosoNoLogueaDatosPersonalesNiDeSalud(CapturedOutput salida) throws Exception {
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		sinDatosSensibles(salida);
	}

	@Test
	void registroFallidoNoLogueaDatosPersonalesNiDeSalud(CapturedOutput salida) throws Exception {
		// Regla incumplida (422), documento duplicado (409), JSON cortado (400) y tipo incorrecto (400).
		enviar(ReservaJson.conAcompanante().set("companion.phone.number", "123")).andExpect(status().isUnprocessableEntity());
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		enviar(ReservaJson.conAcompanante()).andExpect(status().isConflict());
		enviar("{\"firstName\":\"Ana María\",\"document\":\"1065123456\",\"health\":{\"allergies\":\"Penicilina\"")
				.andExpect(status().isBadRequest());
		enviar(ReservaJson.conAcompanante().set("health.exercises", "Penicilina")).andExpect(status().isBadRequest());
		sinDatosSensibles(salida);
	}

	@Test
	void violacionDelIndiceUnicoNoLogueaElDocumento(CapturedOutput salida) throws Exception {
		// Estado inconsistente a propósito: rechazada pero con documento_activo, para que lo rechace el índice.
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		jdbc.update("update pago set revision = 'RECHAZADO', motivo_rechazo = 'x', revisado_en = now()");
		enviar(ReservaJson.conAcompanante().set("document", "1065000111").set("email", "nuevo@example.com")
				.set("phone.number", "3002223344")).andExpect(status().isConflict());
		sinDatosSensibles(salida);
	}

}
