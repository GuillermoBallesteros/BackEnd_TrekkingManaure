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
import co.manaure.trekking.regla.Fechas;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import tools.jackson.databind.JsonNode;

/** HU-N11 y HU-N12: capacidad del páramo y fecha de cierre (RN-09, RN-10, RN-17). */
@Service
public class ConfiguracionAdminService {

	private final ConfiguracionService configuracion;
	private final InscripcionParticipanteRepository participantes;
	private final AuditoriaService auditoria;
	private final Clock clock;

	public ConfiguracionAdminService(ConfiguracionService configuracion,
			InscripcionParticipanteRepository participantes, AuditoriaService auditoria, Clock clock) {
		this.configuracion = configuracion;
		this.participantes = participantes;
		this.auditoria = auditoria;
		this.clock = clock;
	}

	/**
	 * RN-17: mínimo max(1, ocupados). Los ocupados se calculan con el bloqueo de configuracion_evento tomado, así una
	 * aprobación simultánea (que pide el mismo bloqueo) no puede dejar la capacidad por debajo de lo ocupado.
	 */
	@Transactional
	public int fijarCapacidad(JsonNode valor, Long administradorId) {
		if (valor == null || !valor.isIntegralNumber() || !valor.canConvertToInt()) {
			throw capacidadInvalida("Los cupos del páramo deben ser un número entero.");
		}
		int nueva = valor.intValue();
		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		long ocupados = participantes.contarOcupados(-1L);
		if (nueva < Math.max(1, ocupados)) {
			throw capacidadInvalida(ocupados > 0
					? "Los cupos del páramo no pueden ser menos que los " + ocupados + " ya ocupados."
					: "Debe haber al menos 1 cupo en el páramo.");
		}
		int anterior = cfg.getCuposParamo();
		cfg.fijarCupos(nueva, administradorId, OffsetDateTime.now(clock));
		auditoria.registrar(administradorId, AccionAuditoria.FIJAR_CAPACIDAD, EntidadAuditoria.CONFIGURACION,
				"capacidad", "anterior=" + anterior + ", nuevo=" + nueva);
		return nueva;
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

	private static NegocioException capacidadInvalida(String detalle) {
		return new NegocioException(CodigoError.CAPACIDAD_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY, detalle,
				Map.of("campo", "capacity"));
	}

	private static NegocioException fechaInvalida(String detalle) {
		return new NegocioException(CodigoError.FECHA_CIERRE_INVALIDA, HttpStatus.UNPROCESSABLE_ENTITY, detalle,
				Map.of("campo", "fechaCierre"));
	}

}
