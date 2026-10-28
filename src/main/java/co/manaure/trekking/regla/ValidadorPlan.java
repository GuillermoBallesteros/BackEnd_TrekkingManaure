package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.Optional;

import co.manaure.trekking.comun.error.CodigoError;

/** Forma de pago y fecha compromiso del restante. */
public final class ValidadorPlan {

	public static final String PLAN_TOTAL = "total";
	public static final String PLAN_APARTADO = "apartado";

	private ValidadorPlan() {
	}

	public static Optional<ErrorRegla> validar(String plan, String fechaCompromiso, LocalDate hoy,
			ParametrosEvento evento) {
		if (!PLAN_TOTAL.equals(plan) && !PLAN_APARTADO.equals(plan)) {
			return ErrorRegla.de(CodigoError.PLAN_INVALIDO, "Elige la forma de pago.", "plan");
		}
		if (PLAN_APARTADO.equals(plan)) {
			LocalDate primero = evento.primerDiaElegible(hoy);
			if (primero.isAfter(evento.fechaCierre())) {
				return ErrorRegla.de(CodigoError.FECHA_COMPROMISO_INVALIDA,
						"Ya pasó la fecha límite para apartar; elige pagar el total.", "plan");
			}
			Optional<LocalDate> fecha = Fechas.parsear(fechaCompromiso);
			if (fecha.isEmpty() || fecha.get().isBefore(primero) || fecha.get().isAfter(evento.fechaCierre())) {
				return ErrorRegla.de(CodigoError.FECHA_COMPROMISO_INVALIDA,
						"Elige la fecha en que pagarás el restante.", "commitmentDate");
			}
		} else if (fechaCompromiso != null) {
			return ErrorRegla.de(CodigoError.FECHA_COMPROMISO_INVALIDA,
					"El pago total no lleva fecha para el restante.", "commitmentDate");
		}
		return Optional.empty();
	}

}
