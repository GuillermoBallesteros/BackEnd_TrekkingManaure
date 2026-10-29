package co.manaure.trekking.notificacion;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.reserva.RolParticipante;

/**
 * Qué avisos de RN-24 tocan hoy (diseño §18). Funciones puras, sin Spring ni JPA: reciben el estado derivado y las
 * fechas, y devuelven los avisos con su referencia. Solo se avisa en el día exacto: un día perdido no se recupera
 * después.
 */
public final class DecisorAvisos {

	private DecisorAvisos() {
	}

	/**
	 * Recordatorios de la tarea diaria. Saldo pendiente con compromiso a 3, 1 o 0 días; Vencido con compromiso ayer;
	 * Pagado en la víspera del evento, un aviso por cada persona de la inscripción.
	 */
	public static List<Aviso> recordatorios(EstadoInscripcion estado, LocalDate fechaCompromiso,
			LocalDate inicioEvento, LocalDate hoy, Collection<RolParticipante> personas) {
		List<Aviso> avisos = new ArrayList<>();
		if (fechaCompromiso != null) {
			long dias = ChronoUnit.DAYS.between(hoy, fechaCompromiso);
			String referencia = fechaCompromiso.toString();
			if (estado == EstadoInscripcion.SALDO_PENDIENTE) {
				TipoAviso tipo = dias == 3 ? TipoAviso.PAGO_3_DIAS
						: dias == 1 ? TipoAviso.PAGO_1_DIA : dias == 0 ? TipoAviso.PAGO_HOY : null;
				if (tipo != null) {
					avisos.add(new Aviso(tipo, referencia, RolParticipante.TITULAR));
				}
			}
			else if (estado == EstadoInscripcion.VENCIDO && dias == -1) {
				avisos.add(new Aviso(TipoAviso.PAGO_VENCIDO, referencia, RolParticipante.TITULAR));
			}
		}
		if (estado == EstadoInscripcion.PAGADO && inicioEvento != null && hoy.equals(inicioEvento.minusDays(1))) {
			for (RolParticipante rol : RolParticipante.values()) {
				if (personas.contains(rol)) {
					avisos.add(new Aviso(TipoAviso.EVENTO_MANANA, hoy + "/" + rol.name(), rol));
				}
			}
		}
		return avisos;
	}

	/** Aviso de una revisión: aprobado o rechazado, uno por pago; devolver a "Por revisar" no avisa. */
	public static Optional<Aviso> porRevision(RevisionPago revision, String codigoPago) {
		return switch (revision) {
			case APROBADO -> Optional.of(new Aviso(TipoAviso.PAGO_APROBADO, codigoPago, RolParticipante.TITULAR));
			case RECHAZADO -> Optional.of(new Aviso(TipoAviso.PAGO_RECHAZADO, codigoPago, RolParticipante.TITULAR));
			case POR_REVISAR -> Optional.empty();
		};
	}

}
