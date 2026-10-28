package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.List;

import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.modelo.PagoRegla;

/** Estado de una inscripción, derivado de sus pagos (port de {@code getStatus}). No se guarda. */
public enum EstadoInscripcion {

	POR_VERIFICAR("Por verificar"),
	SALDO_PENDIENTE("Saldo pendiente"),
	VENCIDO("Vencido"),
	PAGADO("Pagado"),
	RECHAZADO("Rechazado");

	private final String etiqueta;

	EstadoInscripcion(String etiqueta) {
		this.etiqueta = etiqueta;
	}

	/** Texto que usa el front. */
	public String etiqueta() {
		return etiqueta;
	}

	/** Una inscripción está activa (retiene su documento) mientras no esté rechazada (RN-06). */
	public boolean activa() {
		return this != RECHAZADO;
	}

	/** Suma de los pagos aprobados. */
	public static long pagado(List<PagoRegla> pagos) {
		return pagos.stream().filter(p -> p.revision() == RevisionPago.APROBADO).mapToLong(PagoRegla::monto).sum();
	}

	public static long saldo(long total, List<PagoRegla> pagos) {
		return Math.max(0, total - pagado(pagos));
	}

	/**
	 * Orden: aprobado cubre el total, Pagado; aprobado parcial, Vencido si la fecha compromiso ya pasó o Saldo
	 * pendiente; sin pagos o con alguno por revisar, Por verificar; si no, Rechazado.
	 */
	public static EstadoInscripcion derivar(long total, List<PagoRegla> pagos, LocalDate fechaCompromiso,
			LocalDate hoy) {
		long pagado = pagado(pagos);
		if (pagado > 0 && pagado >= total) {
			return PAGADO;
		}
		if (pagado > 0) {
			return fechaCompromiso != null && fechaCompromiso.isBefore(hoy) ? VENCIDO : SALDO_PENDIENTE;
		}
		if (pagos.isEmpty() || pagos.stream().anyMatch(p -> p.revision() == RevisionPago.POR_REVISAR)) {
			return POR_VERIFICAR;
		}
		return RECHAZADO;
	}

}
