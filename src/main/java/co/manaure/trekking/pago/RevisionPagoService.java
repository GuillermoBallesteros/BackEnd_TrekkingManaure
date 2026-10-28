package co.manaure.trekking.pago;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.regla.modelo.PagoRegla;
import co.manaure.trekking.reserva.Inscripcion;
import co.manaure.trekking.reserva.InscripcionParticipante;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import co.manaure.trekking.reserva.InscripcionRepository;
import co.manaure.trekking.reserva.ReservaAdminService;

/**
 * Revisión de pagos (HU-N08): aprobar, rechazar o devolver a "Por revisar". Una sola transacción que toma primero
 * el bloqueo global de la configuración y después la fila de la inscripción (diseño §9, orden fijo). Dentro
 * recalcula el estado derivado y mantiene {@code inscripcion.activa} y {@code documento_activo} coherentes.
 * Las validaciones de la aprobación viven en {@link AprobacionPago} (compartidas con el pago manual, HU-N09).
 */
@Service
public class RevisionPagoService {

	static final int MOTIVO_MAXIMO = 200;

	private final ConfiguracionService configuracion;
	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final PagoRepository pagos;
	private final AprobacionPago aprobacion;
	private final AuditoriaService auditoria;
	private final ReservaAdminService reservas;
	private final Clock clock;

	public RevisionPagoService(ConfiguracionService configuracion, InscripcionRepository inscripciones,
			InscripcionParticipanteRepository participantes, PagoRepository pagos, AprobacionPago aprobacion,
			AuditoriaService auditoria, ReservaAdminService reservas, Clock clock) {
		this.configuracion = configuracion;
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.pagos = pagos;
		this.aprobacion = aprobacion;
		this.auditoria = auditoria;
		this.reservas = reservas;
		this.clock = clock;
	}

	@Transactional
	public Registration revisar(String codigoPago, RevisionPago nueva, String motivo, Long administradorId) {
		// Se resuelve el código de la inscripción sin cargar el pago: se recarga con los bloqueos tomados.
		String codigoInscripcion = pagos.codigoInscripcionDe(codigoPago).orElseThrow(RevisionPagoService::noExiste);
		String motivoFinal = null;
		if (nueva == RevisionPago.RECHAZADO) {
			motivoFinal = motivo == null ? "" : motivo.strip();
			if (motivoFinal.isEmpty()) {
				throw new NegocioException(CodigoError.MOTIVO_REQUERIDO, HttpStatus.UNPROCESSABLE_ENTITY,
						"Indica el motivo del rechazo.");
			}
			if (motivoFinal.length() > MOTIVO_MAXIMO) {
				motivoFinal = motivoFinal.substring(0, MOTIVO_MAXIMO).stripTrailing();
			}
		}

		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		Inscripcion inscripcion = inscripciones.findByCodigoForUpdate(codigoInscripcion)
				.orElseThrow(RevisionPagoService::noExiste);
		List<Pago> todos = pagos.findByInscripcionIdOrderByIdAsc(inscripcion.getId());
		Pago pago = todos.stream().filter(p -> p.getCodigo().equals(codigoPago)).findFirst()
				.orElseThrow(RevisionPagoService::noExiste);
		RevisionPago anterior = pago.getRevision();

		if (anterior != nueva) {
			aplicar(cfg, inscripcion, pago, todos, nueva, motivoFinal, administradorId);
			auditoria.registrar(administradorId, accion(nueva), EntidadAuditoria.PAGO, pago.getCodigo(),
					anterior + ">" + nueva);
		}
		return reservas.registrations(List.of(inscripcion)).get(0);
	}

	/**
	 * Caducidad (H4): rechaza, en una sola transacción y con el mismo orden de bloqueo (configuración e inscripción),
	 * los pagos "Por revisar" cuya revisión empezó en {@code limite} o antes, solo si la reserva no tiene ningún pago
	 * aprobado. Todo se comprueba de nuevo bajo el bloqueo: si la encargada aprobó o devolvió el pago entretanto, no
	 * hace nada. Reutiliza {@link #aplicar} para dejar coherentes {@code activa} y {@code documento_activo}. Es una
	 * acción del sistema: sin revisor, auditada como RECHAZO_AUTOMATICO sin motivo ni datos personales.
	 *
	 * @return true si rechazó al menos un pago
	 */
	@Transactional
	public boolean rechazarPorCaducidad(String codigoInscripcion, OffsetDateTime limite, String motivo) {
		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		Inscripcion inscripcion = inscripciones.findByCodigoForUpdate(codigoInscripcion).orElse(null);
		if (inscripcion == null) {
			return false;
		}
		List<Pago> todos = pagos.findByInscripcionIdOrderByIdAsc(inscripcion.getId());
		if (todos.stream().anyMatch(p -> p.getRevision() == RevisionPago.APROBADO)) {
			return false;
		}
		boolean alguno = false;
		for (Pago pago : todos) {
			if (pago.getRevision() == RevisionPago.POR_REVISAR && !pago.getEnRevisionDesde().isAfter(limite)) {
				aplicar(cfg, inscripcion, pago, todos, RevisionPago.RECHAZADO, motivo, null);
				auditoria.registrar(null, AccionAuditoria.RECHAZO_AUTOMATICO, EntidadAuditoria.PAGO, pago.getCodigo(),
						RevisionPago.POR_REVISAR + ">" + RevisionPago.RECHAZADO);
				alguno = true;
			}
		}
		return alguno;
	}

	private static NegocioException noExiste() {
		return new NegocioException(CodigoError.PAGO_NO_ENCONTRADO, HttpStatus.NOT_FOUND, "El pago ya no existe.");
	}

	private void aplicar(ConfiguracionEvento cfg, Inscripcion inscripcion, Pago pago, List<Pago> todos,
			RevisionPago nueva, String motivo, Long administradorId) {
		LocalDate hoy = LocalDate.now(clock);
		List<InscripcionParticipante> personas = participantes.porInscripciones(List.of(inscripcion.getId()));

		// Estado resultante, calculado antes de tocar nada para que un error no deje cambios a medias.
		List<PagoRegla> resultantes = todos.stream()
				.map(p -> new PagoRegla(p.getMonto(), p == pago ? nueva : p.getRevision())).toList();
		boolean activaDespues = EstadoInscripcion
				.derivar(inscripcion.getTotal(), resultantes, inscripcion.getFechaCompromiso(), hoy).activa();

		aprobacion.validar(cfg, inscripcion, personas, todos, nueva == RevisionPago.APROBADO, activaDespues, hoy);

		pago.revisar(nueva, motivo, administradorId, OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
		aprobacion.fijarActiva(inscripcion, personas, activaDespues);
	}

	private static AccionAuditoria accion(RevisionPago nueva) {
		return switch (nueva) {
			case APROBADO -> AccionAuditoria.APROBAR_PAGO;
			case RECHAZADO -> AccionAuditoria.RECHAZAR_PAGO;
			case POR_REVISAR -> AccionAuditoria.DEVOLVER_PAGO_A_REVISION;
		};
	}

}
