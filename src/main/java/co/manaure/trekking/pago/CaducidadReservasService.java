package co.manaure.trekking.pago;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import co.manaure.trekking.reserva.Inscripcion;
import co.manaure.trekking.reserva.InscripcionRepository;

/**
 * Caducidad de las reservas sin pago verificado (H4): una reserva sin ningún pago aprobado cuyo pago lleva
 * {@code app.reservas.caducidad.horas} o más en "Por revisar" pasa a Rechazada. Sin transacción propia: busca las
 * candidatas por lotes (paginación por id) y delega cada reserva en {@link RevisionPagoService#rechazarPorCaducidad},
 * que abre su propia transacción, de modo que un fallo no afecta a las demás. Es idempotente y segura si dos
 * instancias se solapan (cada reserva se vuelve a comprobar bajo bloqueo). Solo registra conteos y códigos.
 */
@Service
public class CaducidadReservasService {

	private static final Logger log = LoggerFactory.getLogger(CaducidadReservasService.class);

	private final InscripcionRepository inscripciones;
	private final RevisionPagoService revision;
	private final Clock clock;
	private final long horas;
	private final int lote;

	public CaducidadReservasService(InscripcionRepository inscripciones, RevisionPagoService revision, Clock clock,
			@Value("${app.reservas.caducidad.horas:12}") long horas,
			@Value("${app.reservas.caducidad.lote:50}") int lote) {
		this.inscripciones = inscripciones;
		this.revision = revision;
		this.clock = clock;
		this.horas = horas;
		this.lote = lote;
	}

	/** Texto del rechazo automático; el front lo muestra como motivo. */
	String motivo() {
		return "No se verificó el pago dentro de " + horas + (horas == 1 ? " hora." : " horas.");
	}

	/** @return cantidad de reservas rechazadas en esta ejecución */
	public int ejecutar() {
		OffsetDateTime limite = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS).minusHours(horas);
		String motivo = motivo();
		int rechazadas = 0;
		int fallidas = 0;
		long desde = 0;
		while (true) {
			List<Inscripcion> candidatas = inscripciones.candidatasACaducar(desde, limite, PageRequest.ofSize(lote));
			if (candidatas.isEmpty()) {
				break;
			}
			for (Inscripcion candidata : candidatas) {
				desde = Math.max(desde, candidata.getId());
				try {
					if (revision.rechazarPorCaducidad(candidata.getCodigo(), limite, motivo)) {
						rechazadas++;
					}
				}
				catch (RuntimeException ex) {
					fallidas++;
					log.warn("Caducidad: no se pudo procesar la reserva {} ({}).", candidata.getCodigo(),
							ex.getClass().getSimpleName());
				}
			}
		}
		if (rechazadas > 0 || fallidas > 0) {
			log.info("Caducidad de reservas: {} rechazadas, {} con error.", rechazadas, fallidas);
		}
		return rechazadas;
	}

}
