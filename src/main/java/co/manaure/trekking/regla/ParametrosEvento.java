package co.manaure.trekking.regla;

import java.time.LocalDate;

/** Datos del evento que usan las reglas (vienen de la configuración; aquí solo se reciben). */
public record ParametrosEvento(LocalDate fechaApertura, LocalDate fechaEventoInicio, LocalDate fechaCierre,
		String versionTerminos) {

	/** Primer día elegible: hoy, o la apertura si todavía no llega. */
	public LocalDate primerDiaElegible(LocalDate hoy) {
		return hoy.isAfter(fechaApertura) ? hoy : fechaApertura;
	}

}
