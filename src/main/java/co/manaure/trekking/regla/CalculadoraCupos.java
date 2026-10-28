package co.manaure.trekking.regla;

import java.util.List;

import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.modelo.PagoRegla;

/**
 * Cupos del páramo (port de {@code cupos.ts}): ocupa cupo cada persona con estadía que {@code ocupa_cupo}
 * de una inscripción con al menos un pago aprobado. Los ocupados se calculan siempre, nunca se guardan.
 */
public final class CalculadoraCupos {

	/** Estadías de las personas de una inscripción y sus pagos. */
	public record InscripcionCupo(List<String> estadias, List<PagoRegla> pagos) {
	}

	public record Disponibilidad(int capacidad, int ocupados, int disponibles) {
	}

	private CalculadoraCupos() {
	}

	/** Una inscripción retiene cupo desde que tiene al menos un pago aprobado. */
	public static boolean retieneCupo(List<PagoRegla> pagos) {
		return pagos.stream().anyMatch(p -> p.revision() == RevisionPago.APROBADO);
	}

	/** Personas de la lista que se quedan en el páramo (las únicas que gastan cupo). */
	public static int personasParamo(List<String> estadias, CatalogoReglas catalogos) {
		return (int) estadias.stream().filter(catalogos::ocupaCupo).count();
	}

	public static int ocupados(List<InscripcionCupo> inscripciones, CatalogoReglas catalogos) {
		return inscripciones.stream().filter(i -> retieneCupo(i.pagos()))
				.mapToInt(i -> personasParamo(i.estadias(), catalogos)).sum();
	}

	public static Disponibilidad disponibilidad(List<InscripcionCupo> inscripciones, int capacidad,
			CatalogoReglas catalogos) {
		int ocupados = ocupados(inscripciones, catalogos);
		return new Disponibilidad(capacidad, ocupados, Math.max(0, capacidad - ocupados));
	}

	/** Mínimo permitido al ajustar la capacidad: no menos que lo ocupado, y al menos 1. */
	public static int capacidadMinima(int ocupados) {
		return Math.max(1, ocupados);
	}

}
