package co.manaure.trekking.notificacion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** HU-N15: si la API arranca después de las 12:00 en Bogotá, corre una vez los avisos del día. */
class RecordatorioPagoTaskTest {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	private final RecordatorioPagoService servicio = mock(RecordatorioPagoService.class);

	private static Clock a(int hora, int minuto) {
		return Clock.fixed(ZonedDateTime.of(2026, 11, 6, hora, minuto, 0, 0, BOGOTA).toInstant(), BOGOTA);
	}

	private RecordatorioPagoTask tarea(boolean recuperar, Clock clock) {
		PropiedadesBrevo propiedades = new PropiedadesBrevo(true, "clave", "https://api.brevo.test/v3",
				"a@b.co", "X", 50, 0, recuperar, Map.of());
		return new RecordatorioPagoTask(servicio, propiedades, clock);
	}

	@Test
	void alArrancarDespuesDeLasDoceCorreUnaVez() {
		tarea(true, a(12, 0)).alArrancar();
		verify(servicio).ejecutar();
	}

	@Test
	void alArrancarAntesDeLasDoceNoCorre() {
		tarea(true, a(11, 59)).alArrancar();
		verify(servicio, never()).ejecutar();
	}

	@Test
	void conLaRecuperacionApagadaNoCorre() {
		tarea(false, a(15, 0)).alArrancar();
		verify(servicio, never()).ejecutar();
	}

	@Test
	void unFalloAlArrancarNoImpideQueLaApiArranque() {
		when(servicio.ejecutar()).thenThrow(new IllegalStateException("base caída"));
		assertThatCode(() -> tarea(true, a(13, 0)).alArrancar()).doesNotThrowAnyException();
	}

}
