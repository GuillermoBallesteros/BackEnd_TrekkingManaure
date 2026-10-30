package co.manaure.trekking.reserva;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;

/**
 * Lectura de la disponibilidad (RN-04): cupos de la carrera (una reserva activa gasta uno) y cupos del páramo (una
 * por persona). Los ocupados se calculan siempre; nunca se guardan.
 */
@Service
public class DisponibilidadService {

	public record Resumen(int capacidad, int ocupados, int disponibles, int paramoCapacidad, int paramoOcupados,
			int paramoDisponibles, LocalDate fechaCierre, LocalDate hoy) {
	}

	private final ConfiguracionService configuracion;
	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final Clock clock;

	public DisponibilidadService(ConfiguracionService configuracion, InscripcionRepository inscripciones,
			InscripcionParticipanteRepository participantes, Clock clock) {
		this.configuracion = configuracion;
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Resumen consultar() {
		ConfiguracionEvento cfg = configuracion.obtener();
		int ocupados = Math.toIntExact(inscripciones.contarActivas());
		int capacidad = cfg.getCuposCarrera();
		int paramoOcupados = Math.toIntExact(participantes.contarOcupados(-1L));
		int paramoCapacidad = cfg.getCuposParamo();
		return new Resumen(capacidad, ocupados, Math.max(0, capacidad - ocupados), paramoCapacidad, paramoOcupados,
				Math.max(0, paramoCapacidad - paramoOcupados), cfg.getFechaCierre(), LocalDate.now(clock));
	}

}
