package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lo que necesita validar una reserva completa además de sus datos: catálogos, evento, el día de hoy en
 * Bogotá, los documentos de las inscripciones activas (RN-06) y los cupos libres del páramo.
 */
public record ContextoReserva(CatalogoReglas catalogos, ParametrosEvento evento, LocalDate hoy,
		Set<String> documentosActivos, int cuposDisponibles) {

	/** Los documentos se guardan normalizados (sin espacios, en mayúsculas) para comparar. */
	public ContextoReserva {
		documentosActivos = documentosActivos.stream().map(Textos::normalizarDocumento)
				.collect(Collectors.toUnmodifiableSet());
	}

}
