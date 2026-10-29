package co.manaure.trekking.notificacion;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.notificacion.ContextoAviso.PagoAviso;
import co.manaure.trekking.notificacion.ContextoAviso.PersonaAviso;
import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.pago.PagoRepository;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.regla.modelo.PagoRegla;
import co.manaure.trekking.reserva.Inscripcion;
import co.manaure.trekking.reserva.InscripcionParticipante;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import co.manaure.trekking.reserva.InscripcionRepository;
import co.manaure.trekking.reserva.RolParticipante;

/**
 * Lecturas de los avisos, cada una en una transacción nueva de solo lectura: tras el commit de una revisión
 * (AFTER_COMMIT) no se reutiliza la transacción terminada y siempre se lee lo ya confirmado.
 */
@Service
public class LectorAvisos {

	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final PagoRepository pagos;
	private final ConfiguracionService configuracion;

	public LectorAvisos(InscripcionRepository inscripciones, InscripcionParticipanteRepository participantes,
			PagoRepository pagos, ConfiguracionService configuracion) {
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.pagos = pagos;
		this.configuracion = configuracion;
	}

	/** Ids de las inscripciones activas (no Rechazadas) con id mayor que {@code desde}, por id ascendente. */
	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public List<Long> idsActivas(long desde, int lote) {
		return inscripciones.idsActivas(desde, PageRequest.ofSize(lote));
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public Optional<ContextoAviso> porInscripcion(long inscripcionId, LocalDate hoy) {
		return inscripciones.findById(inscripcionId).map(i -> contexto(i, hoy));
	}

	@Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
	public Optional<ContextoAviso> porPago(String codigoPago, LocalDate hoy) {
		return pagos.codigoInscripcionDe(codigoPago).flatMap(inscripciones::findByCodigo).map(i -> contexto(i, hoy));
	}

	private ContextoAviso contexto(Inscripcion inscripcion, LocalDate hoy) {
		List<Pago> todos = pagos.findByInscripcionIdOrderByIdAsc(inscripcion.getId());
		List<PagoRegla> reglas = todos.stream().map(p -> new PagoRegla(p.getMonto(), p.getRevision())).toList();
		EstadoInscripcion estado = EstadoInscripcion.derivar(inscripcion.getTotal(), reglas,
				inscripcion.getFechaCompromiso(), hoy);

		Map<RolParticipante, PersonaAviso> personas = new EnumMap<>(RolParticipante.class);
		for (InscripcionParticipante ip : participantes.porInscripciones(List.of(inscripcion.getId()))) {
			personas.put(ip.getRol(), new PersonaAviso(ip.getPersona().getNombres(), ip.getPersona().getApellidos(),
					ip.getPersona().getCorreo()));
		}
		List<PagoAviso> pagosAviso = todos.stream().map(p -> new PagoAviso(p.getCodigo(), p.getConcepto(),
				p.getMonto(), p.getRevision(), p.getMotivoRechazo())).toList();

		ConfiguracionEvento cfg = configuracion.obtener();
		return new ContextoAviso(inscripcion.getId(), inscripcion.getCodigo(), estado,
				inscripcion.getFechaCompromiso(), inscripcion.getTotal(), EstadoInscripcion.pagado(reglas),
				EstadoInscripcion.saldo(inscripcion.getTotal(), reglas), cfg.getNombreEvento(),
				cfg.getFechaEventoInicio(), Map.copyOf(personas), pagosAviso);
	}

}
