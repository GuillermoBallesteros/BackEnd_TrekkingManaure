package co.manaure.trekking.notificacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Tarea diaria de avisos (RN-24): recorre por lotes las inscripciones activas y envía los recordatorios que tocan
 * hoy. Cada inscripción va aislada: un fallo se registra (solo id y clase del error) y no frena a las demás. Es
 * idempotente: correrla otra vez el mismo día no reenvía nada.
 */
@Service
@ConditionalOnProperty(name = "app.brevo.activo", havingValue = "true")
public class RecordatorioPagoService {

	private static final Logger log = LoggerFactory.getLogger(RecordatorioPagoService.class);

	private final LectorAvisos lector;
	private final ServicioAvisos avisos;
	private final PropiedadesBrevo propiedades;
	private final Clock clock;

	public RecordatorioPagoService(LectorAvisos lector, ServicioAvisos avisos, PropiedadesBrevo propiedades,
			Clock clock) {
		this.lector = lector;
		this.avisos = avisos;
		this.propiedades = propiedades;
		this.clock = clock;
	}

	/** @return cantidad de avisos enviados en esta ejecución */
	public int ejecutar() {
		LocalDate hoy = LocalDate.now(clock);
		int lote = Math.max(1, propiedades.lote());
		int enviados = 0;
		int fallidas = 0;
		long desde = 0;
		while (true) {
			List<Long> ids = lector.idsActivas(desde, lote);
			if (ids.isEmpty()) {
				break;
			}
			for (Long id : ids) {
				desde = Math.max(desde, id);
				try {
					enviados += avisos.recordar(id, hoy);
				}
				catch (RuntimeException ex) {
					fallidas++;
					log.warn("Avisos: no se pudo procesar la inscripción {} ({}).", id, ex.getClass().getSimpleName());
				}
			}
		}
		if (enviados > 0 || fallidas > 0) {
			log.info("Avisos del día: {} enviados, {} inscripciones con error.", enviados, fallidas);
		}
		return enviados;
	}

}
