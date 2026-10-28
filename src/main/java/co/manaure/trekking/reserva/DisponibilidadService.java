package co.manaure.trekking.reserva;

import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;

/** Lectura de la disponibilidad del páramo (RN-04). Los ocupados se calculan siempre; nunca se guardan. */
@Service
public class DisponibilidadService {

	public record Resumen(int capacidad, int ocupados, int disponibles, LocalDate fechaCierre, LocalDate hoy) {
	}

	private final ConfiguracionService configuracion;
	private final InscripcionParticipanteRepository participantes;
	private final Clock clock;

	public DisponibilidadService(ConfiguracionService configuracion, InscripcionParticipanteRepository participantes,
			Clock clock) {
		this.configuracion = configuracion;
		this.participantes = participantes;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Resumen consultar() {
		ConfiguracionEvento cfg = configuracion.obtener();
		int ocupados = Math.toIntExact(participantes.contarOcupados(-1L));
		int capacidad = cfg.getCuposParamo();
		return new Resumen(capacidad, ocupados, Math.max(0, capacidad - ocupados), cfg.getFechaCierre(),
				LocalDate.now(clock));
	}

}
