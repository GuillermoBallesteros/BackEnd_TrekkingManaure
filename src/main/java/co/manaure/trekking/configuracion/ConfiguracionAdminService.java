package co.manaure.trekking.configuracion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.api.dto.ConfiguracionAdminResponses.Capacidad;
import co.manaure.trekking.regla.Fechas;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import co.manaure.trekking.reserva.InscripcionRepository;
import tools.jackson.databind.JsonNode;

/** HU-N11 y HU-N12: cupos de la carrera y del páramo, y fecha de cierre (RN-09, RN-10, RN-17). */
@Service
public class ConfiguracionAdminService {

	private final ConfiguracionService configuracion;
	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final AuditoriaService auditoria;
	private final Clock clock;

	public ConfiguracionAdminService(ConfiguracionService configuracion, InscripcionRepository inscripciones,
			InscripcionParticipanteRepository participantes, AuditoriaService auditoria, Clock clock) {
		this.configuracion = configuracion;
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.clock = clock;
	}

	/**
	 * RN-17: cada tope tiene como mínimo max(1, ocupados). Los ocupados se calculan con el bloqueo de
	 * configuracion_evento tomado, así una reserva simultánea (que pide el mismo bloqueo) no puede dejar un tope por
	 * debajo de lo ocupado.
	 */
	@Transactional
	public Capacidad fijarCapacidad(JsonNode carrera, JsonNode paramo, Long administradorId) {
		if (carrera == null || !carrera.isIntegralNumber() || !carrera.canConvertToInt()) {
			throw capacidadInvalida("Los cupos de la carrera deben ser un número entero.", "capacity");
		}
		if (paramo == null || !paramo.isIntegralNumber() || !paramo.canConvertToInt()) {
			throw capacidadInvalida("Los cupos del páramo deben ser un número entero.", "paramo");
		}
		int nuevaCarrera = carrera.intValue();
		int nuevoParamo = paramo.intValue();
		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		long reservas = inscripciones.contarActivas();
		if (nuevaCarrera < Math.max(1, reservas)) {
			throw capacidadInvalida(reservas > 0
					? "Los cupos de la carrera no pueden ser menos que las " + reservas + " reservas activas."
					: "Debe haber al menos 1 cupo en la carrera.", "capacity");
		}
		long ocupadosParamo = participantes.contarOcupados(-1L);
		if (nuevoParamo < Math.max(1, ocupadosParamo)) {
			throw capacidadInvalida(ocupadosParamo > 0
					? "Los cupos del páramo no pueden ser menos que los " + ocupadosParamo + " cupos ya ocupados."
					: "Debe haber al menos 1 cupo en el páramo.", "paramo");
		}
		int anteriorCarrera = cfg.getCuposCarrera();
		int anteriorParamo = cfg.getCuposParamo();
		cfg.fijarCupos(nuevaCarrera, nuevoParamo, administradorId, OffsetDateTime.now(clock));
		auditoria.registrar(administradorId, AccionAuditoria.FIJAR_CAPACIDAD, EntidadAuditoria.CONFIGURACION,
				"capacidad", "carrera anterior=" + anteriorCarrera + ", nuevo=" + nuevaCarrera + "; paramo anterior="
						+ anteriorParamo + ", nuevo=" + nuevoParamo);
		return new Capacidad(nuevaCarrera, nuevoParamo);
	}

	/** RN-09/RN-10: entre la apertura (inclusive) y el primer día del evento (exclusive); puede quedar en el pasado. */
	@Transactional
	public LocalDate fijarFechaCierre(JsonNode valor, Long administradorId) {
		LocalDate nueva = valor != null && valor.isString() ? Fechas.parsear(valor.stringValue()).orElse(null) : null;
		if (nueva == null) {
			throw fechaInvalida("Elige una fecha de cierre válida.");
		}
		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		if (nueva.isBefore(cfg.getFechaApertura())) {
			throw fechaInvalida("La fecha de cierre no puede ser anterior a la apertura de inscripciones.");
		}
		if (!nueva.isBefore(cfg.getFechaEventoInicio())) {
			throw fechaInvalida("La fecha de cierre debe ser antes del evento.");
		}
		LocalDate anterior = cfg.getFechaCierre();
		cfg.fijarFechaCierre(nueva, administradorId, OffsetDateTime.now(clock));
		auditoria.registrar(administradorId, AccionAuditoria.FIJAR_FECHA_CIERRE, EntidadAuditoria.CONFIGURACION,
				"fecha_cierre", "anterior=" + anterior + ", nuevo=" + nueva);
		return nueva;
	}

	private static NegocioException capacidadInvalida(String detalle, String campo) {
		return new NegocioException(CodigoError.CAPACIDAD_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY, detalle,
				Map.of("campo", campo));
	}

	private static NegocioException fechaInvalida(String detalle) {
		return new NegocioException(CodigoError.FECHA_CIERRE_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY, detalle,
				Map.of("campo", "fechaCierre"));
	}

}
