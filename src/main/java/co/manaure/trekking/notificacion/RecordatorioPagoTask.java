package co.manaure.trekking.notificacion;

import java.time.Clock;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Programación de los avisos del día (RN-24): a las 12:00 en Bogotá según {@code app.brevo.cron}. Si la API arranca
 * después de esa hora, corre una vez al quedar lista, para no perder los avisos de hoy (con
 * {@code app.brevo.recuperar-al-arrancar}; las pruebas lo apagan).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.brevo.activo", havingValue = "true")
public class RecordatorioPagoTask {

	private static final Logger log = LoggerFactory.getLogger(RecordatorioPagoTask.class);
	static final LocalTime HORA_ENVIO = LocalTime.NOON;

	private final RecordatorioPagoService servicio;
	private final PropiedadesBrevo propiedades;
	private final Clock clock;

	public RecordatorioPagoTask(RecordatorioPagoService servicio, PropiedadesBrevo propiedades, Clock clock) {
		this.servicio = servicio;
		this.propiedades = propiedades;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.brevo.cron:0 0 12 * * *}", zone = "America/Bogota")
	public void ejecutar() {
		servicio.ejecutar();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void alArrancar() {
		if (!propiedades.recuperarAlArrancar() || LocalTime.now(clock).isBefore(HORA_ENVIO)) {
			return;
		}
		try {
			servicio.ejecutar();
		}
		catch (RuntimeException ex) {
			// Un fallo aquí no debe impedir que la API arranque; la tarea de mañana lo vuelve a intentar.
			log.warn("Avisos: la ejecución al arrancar falló ({}).", ex.getClass().getSimpleName());
		}
	}

}
