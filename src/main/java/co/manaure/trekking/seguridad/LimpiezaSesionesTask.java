package co.manaure.trekking.seguridad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Borra de {@code sesion_refresh} los refresh vencidos hace más de 30 días (diseño §10.1). Se activa con
 * {@code app.seguridad.limpieza.activa=true} (por defecto sí; las pruebas la dejan apagada) y corre a diario.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.seguridad.limpieza.activa", havingValue = "true")
public class LimpiezaSesionesTask {

	private static final Logger log = LoggerFactory.getLogger(LimpiezaSesionesTask.class);
	static final Duration RETENCION = Duration.ofDays(30);
	static final Duration RETENCION_INTENTOS = Duration.ofDays(1);

	private final RefreshTokenService refreshService;
	private final IntentoLoginRepository intentos;
	private final Clock clock;

	public LimpiezaSesionesTask(RefreshTokenService refreshService, IntentoLoginRepository intentos, Clock clock) {
		this.refreshService = refreshService;
		this.intentos = intentos;
		this.clock = clock;
	}

	@Scheduled(cron = "${app.seguridad.limpieza.cron:0 30 3 * * *}", zone = "America/Bogota")
	public void limpiar() {
		Instant limite = clock.instant().minus(RETENCION);
		int borrados = refreshService.borrarVencidosAntesDe(limite);
		log.info("Limpieza de sesiones: {} refresh vencidos borrados.", borrados);
		int intentosBorrados = limpiarIntentos();
		log.info("Limpieza de intentos de login: {} filas borradas.", intentosBorrados);
	}

	/** Borra los intentos de login cuyo ultimo fallo y bloqueo son anteriores a la retencion de intentos. */
	public int limpiarIntentos() {
		return intentos.borrarAntesDe(clock.instant().minus(RETENCION_INTENTOS));
	}

}
