package co.manaure.trekking.pago;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tarea programada de la caducidad de reservas sin pago verificado (H4). Se activa con
 * {@code app.reservas.caducidad.activa=true} (por defecto sí; las pruebas la dejan apagada) y corre según
 * {@code app.reservas.caducidad.cron} (cada 10 minutos por defecto).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.reservas.caducidad.activa", havingValue = "true")
public class CaducidadReservasTask {

	private final CaducidadReservasService servicio;

	public CaducidadReservasTask(CaducidadReservasService servicio) {
		this.servicio = servicio;
	}

	@Scheduled(cron = "${app.reservas.caducidad.cron:0 */10 * * * *}", zone = "America/Bogota")
	public void ejecutar() {
		servicio.ejecutar();
	}

}
