package co.manaure.trekking.reserva;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.pago.AprobacionPago;
import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.pago.PagoRepository;
import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.regla.modelo.PagoRegla;

/**
 * Reservas vencidas (saldo sin pagar después de la fecha compromiso): la encargada amplía el plazo (RN-25) o la
 * rechaza para liberar sus cupos (RN-26). Pagar el saldo sigue siendo el pago manual (HU-N09). Mismo orden de
 * bloqueos que la revisión de pagos: configuración y luego la inscripción (diseño §9); el estado se recalcula dentro.
 */
@Service
public class VencimientoService {

	public static final int DIAS_MINIMOS = 1;
	public static final int DIAS_MAXIMOS = 5;

	private final ConfiguracionService configuracion;
	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final PagoRepository pagos;
	private final AprobacionPago aprobacion;
	private final AuditoriaService auditoria;
	private final ReservaAdminService reservas;
	private final Clock clock;
	private final ApplicationEventPublisher eventos;

	public VencimientoService(ConfiguracionService configuracion, InscripcionRepository inscripciones,
			InscripcionParticipanteRepository participantes, PagoRepository pagos, AprobacionPago aprobacion,
			AuditoriaService auditoria, ReservaAdminService reservas, Clock clock, ApplicationEventPublisher eventos) {
		this.configuracion = configuracion;
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.pagos = pagos;
		this.aprobacion = aprobacion;
		this.auditoria = auditoria;
		this.reservas = reservas;
		this.clock = clock;
		this.eventos = eventos;
	}

	/**
	 * RN-25: la nueva fecha compromiso es hoy más {@code dias} (1 a 5), sin tope por la fecha de cierre. La reserva
	 * vuelve a Saldo pendiente y los recordatorios se rearman con la nueva fecha.
	 *
	 * @param dias {@code null} si el cliente no envió un entero
	 */
	@Transactional
	public Registration ampliarPlazo(String codigo, Integer dias, Long administradorId) {
		if (dias == null || dias < DIAS_MINIMOS || dias > DIAS_MAXIMOS) {
			throw new NegocioException(CodigoError.PLAZO_INVALIDO, HttpStatus.UNPROCESSABLE_ENTITY,
					"Elige entre " + DIAS_MINIMOS + " y " + DIAS_MAXIMOS + " días.");
		}
		configuracion.obtenerConBloqueo();
		Inscripcion inscripcion = bloquear(codigo);
		LocalDate hoy = LocalDate.now(clock);
		exigirVencida(inscripcion, pagos.findByInscripcionIdOrderByIdAsc(inscripcion.getId()), hoy);

		LocalDate nueva = hoy.plusDays(dias);
		inscripcion.ampliarPlazo(nueva);
		auditoria.registrar(administradorId, AccionAuditoria.AMPLIAR_PLAZO, EntidadAuditoria.RESERVA,
				inscripcion.getCodigo(), "dias=" + dias + " hasta=" + nueva);
		eventos.publishEvent(new ReservaCambiadaEvent(inscripcion.getCodigo(),
				ReservaCambiadaEvent.Cambio.PLAZO_AMPLIADO, nueva.toString()));
		return reservas.registrations(List.of(inscripcion)).get(0);
	}

	/**
	 * RN-26: la reserva vencida queda Rechazada e inactiva y libera documento, cupo de la carrera y camas del páramo.
	 * Sus pagos aprobados no cambian (son definitivos, RN-14) y no hay reembolso en el sistema. Si hay un pago por
	 * revisar, primero se decide ese pago.
	 */
	@Transactional
	public Registration rechazar(String codigo, Long administradorId) {
		configuracion.obtenerConBloqueo();
		Inscripcion inscripcion = bloquear(codigo);
		List<Pago> todos = pagos.findByInscripcionIdOrderByIdAsc(inscripcion.getId());
		exigirVencida(inscripcion, todos, LocalDate.now(clock));
		if (todos.stream().anyMatch(p -> p.getRevision() == RevisionPago.POR_REVISAR)) {
			throw new NegocioException(CodigoError.PAGO_POR_REVISAR_PENDIENTE, HttpStatus.CONFLICT,
					"Esta reserva tiene un pago por revisar: apruébalo o recházalo antes de rechazar la reserva.");
		}

		inscripcion.rechazar(OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
		aprobacion.fijarActiva(inscripcion, participantes.porInscripciones(List.of(inscripcion.getId())), false);
		auditoria.registrar(administradorId, AccionAuditoria.RECHAZAR_RESERVA, EntidadAuditoria.RESERVA,
				inscripcion.getCodigo(), EstadoInscripcion.VENCIDO + ">" + EstadoInscripcion.RECHAZADO);
		eventos.publishEvent(new ReservaCambiadaEvent(inscripcion.getCodigo(), ReservaCambiadaEvent.Cambio.RECHAZADA,
				inscripcion.getCodigo()));
		return reservas.registrations(List.of(inscripcion)).get(0);
	}

	private Inscripcion bloquear(String codigo) {
		return inscripciones.findByCodigoForUpdate(codigo).orElseThrow(() -> new NegocioException(
				CodigoError.RESERVA_NO_ENCONTRADA, HttpStatus.NOT_FOUND, "La inscripción ya no existe."));
	}

	private static void exigirVencida(Inscripcion inscripcion, List<Pago> todos, LocalDate hoy) {
		List<PagoRegla> reglas = todos.stream().map(p -> new PagoRegla(p.getMonto(), p.getRevision())).toList();
		EstadoInscripcion estado = EstadoInscripcion.derivar(inscripcion.getTotal(), reglas,
				inscripcion.getFechaCompromiso(), hoy, inscripcion.isRechazada());
		if (estado != EstadoInscripcion.VENCIDO) {
			throw new NegocioException(CodigoError.RESERVA_NO_VENCIDA, HttpStatus.CONFLICT,
					"Solo se puede hacer con una reserva vencida (está en " + estado.etiqueta() + ").");
		}
	}

}
