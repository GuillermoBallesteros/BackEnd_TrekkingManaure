package co.manaure.trekking.api.dto;

import java.time.LocalDate;

/** Respuestas de HU-N11 y HU-N12. */
public final class ConfiguracionAdminResponses {

	private ConfiguracionAdminResponses() {
	}

	public record Capacidad(int capacity, int paramo) {
	}

	public record FechaCierre(LocalDate fechaCierre) {
	}

}
