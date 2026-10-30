package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lo que necesita validar una reserva completa además de sus datos: catálogos, evento, el día de hoy en
 * Bogotá, los documentos de las inscripciones activas (RN-06), los cupos libres de la carrera (una reserva gasta
 * uno) y los cupos libres del páramo (uno por persona).
 */
public record ContextoReserva(CatalogoReglas catalogos, ParametrosEvento evento, LocalDate hoy,
		Set<String> documentosActivos, int cuposDisponibles, int cuposCarreraDisponibles) {

	/** Sin tope de carrera: para quien solo valida el páramo. */
	public ContextoReserva(CatalogoReglas catalogos, ParametrosEvento evento, LocalDate hoy,
			Set<String> documentosActivos, int cuposDisponibles) {
		this(catalogos, evento, hoy, documentosActivos, cuposDisponibles, Integer.MAX_VALUE);
	}

	/** Los documentos se guardan normalizados (sin espacios, en mayúsculas) para comparar. */
	public ContextoReserva {
		documentosActivos = documentosActivos.stream().map(Textos::normalizarDocumento)
				.collect(Collectors.toUnmodifiableSet());
	}

}
