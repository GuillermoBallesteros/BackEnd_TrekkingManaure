package co.manaure.trekking.pago;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.regla.modelo.PagoRegla;
import co.manaure.trekking.reserva.GeneradorCodigos;
import co.manaure.trekking.reserva.Inscripcion;
import co.manaure.trekking.reserva.InscripcionParticipante;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import co.manaure.trekking.reserva.InscripcionRepository;

/**
 * Pago manual (HU-N09, RN-15, RN-16): un pago ya verificado que cuenta de inmediato. Toma el bloqueo global de la
 * configuración y después la fila de la inscripción (diseño §9); el saldo se calcula bajo ese bloqueo, así dos pagos
 * simultáneos no pueden superarlo. Las validaciones de aprobación son las de {@link AprobacionPago}.
 */
@Service
public class PagoManualService {

	private final ConfiguracionService configuracion;
	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final PagoRepository pagos;
	private final AprobacionPago aprobacion;
	private final GeneradorCodigos codigos;
	private final AuditoriaService auditoria;
	private final Clock clock;

	public PagoManualService(ConfiguracionService configuracion, InscripcionRepository inscripciones,
			InscripcionParticipanteRepository participantes, PagoRepository pagos, AprobacionPago aprobacion,
			GeneradorCodigos codigos, AuditoriaService auditoria, Clock clock) {
		this.configuracion = configuracion;
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.pagos = pagos;
		this.aprobacion = aprobacion;
		this.codigos = codigos;
		this.auditoria = auditoria;
		this.clock = clock;
	}

	/** @param monto pesos enteros; {@code null} si el cliente no envió un entero. */
	@Transactional
	public Pago registrar(String codigoReserva, Long monto, Long administradorId) {
		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		Inscripcion inscripcion = inscripciones.findByCodigoForUpdate(codigoReserva)
				.orElseThrow(() -> new NegocioException(CodigoError.RESERVA_NO_ENCONTRADA, HttpStatus.NOT_FOUND,
						"La inscripción ya no existe."));
		if (monto == null || monto <= 0) {
			throw new NegocioException(CodigoError.MONTO_INVALIDO, HttpStatus.UNPROCESSABLE_ENTITY,
					"Escribe un monto válido.");
		}

		LocalDate hoy = LocalDate.now(clock);
		List<Pago> todos = pagos.findByInscripcionIdOrderByIdAsc(inscripcion.getId());
		List<PagoRegla> reglas = todos.stream().map(p -> new PagoRegla(p.getMonto(), p.getRevision())).toList();
		long pagado = EstadoInscripcion.pagado(reglas);
		if (monto > EstadoInscripcion.saldo(inscripcion.getTotal(), reglas)) {
			throw new NegocioException(CodigoError.MONTO_SUPERA_SALDO, HttpStatus.UNPROCESSABLE_ENTITY,
					"El monto supera el saldo pendiente.");
		}

		List<PagoRegla> resultantes = new ArrayList<>(reglas);
		resultantes.add(new PagoRegla(monto, RevisionPago.APROBADO));
		boolean activaDespues = EstadoInscripcion
				.derivar(inscripcion.getTotal(), resultantes, inscripcion.getFechaCompromiso(), hoy).activa();
		List<InscripcionParticipante> personas = participantes.porInscripciones(List.of(inscripcion.getId()));
		aprobacion.validar(cfg, inscripcion, personas, todos, true, activaDespues, hoy);

		Pago pago = Pago.manual(codigos.siguienteCodigoPago(), inscripcion, concepto(pagado, monto, inscripcion.getTotal()),
				monto, hoy, administradorId, OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
		pagos.save(pago);
		aprobacion.fijarActiva(inscripcion, personas, activaDespues);
		auditoria.registrar(administradorId, AccionAuditoria.REGISTRAR_PAGO_MANUAL, EntidadAuditoria.PAGO,
				pago.getCodigo(), inscripcion.getCodigo() + " monto=" + monto);
		return pago;
	}

	/** Port de {@code conceptFor}: con algo pagado es saldo; si no, total o separación según cubra el total. */
	static ConceptoPago concepto(long pagado, long monto, long total) {
		if (pagado > 0) {
			return ConceptoPago.PAGO_SALDO;
		}
		return monto >= total ? ConceptoPago.PAGO_TOTAL : ConceptoPago.SEPARACION_CUPO;
	}

}
