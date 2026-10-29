package co.manaure.trekking.notificacion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import co.manaure.trekking.pago.ConceptoPago;
import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.reserva.RolParticipante;

/**
 * Lo que un aviso necesita de una inscripción, leído en una transacción corta y ya sin entidades JPA: así la llamada
 * a Brevo no ocurre con una transacción abierta. Nunca incluye documento, EPS ni salud.
 */
public record ContextoAviso(long inscripcionId, String codigoReserva, EstadoInscripcion estado,
		LocalDate fechaCompromiso, long total, long pagado, long saldo, String nombreEvento, LocalDate inicioEvento,
		Map<RolParticipante, PersonaAviso> personas, List<PagoAviso> pagos) {

	public Optional<PagoAviso> pago(String codigo) {
		return pagos.stream().filter(p -> p.codigo().equals(codigo)).findFirst();
	}

	/** Nombres y correo de una persona, tal como están en la base (mayúsculas sin tildes). */
	public record PersonaAviso(String nombres, String apellidos, String correo) {

		Destinatario destinatario() {
			return new Destinatario(FormatoAviso.nombrePropio(nombres + " " + apellidos), correo);
		}

		@Override
		public String toString() {
			return "PersonaAviso[***]";
		}

	}

	public record PagoAviso(String codigo, ConceptoPago concepto, long monto, RevisionPago revision, String motivo) {
	}

}
