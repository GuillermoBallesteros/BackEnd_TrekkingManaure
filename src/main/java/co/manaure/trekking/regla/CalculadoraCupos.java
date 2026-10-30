package co.manaure.trekking.regla;

import java.util.List;

/**
 * Cupos del páramo: gasta uno cada persona, titular o acompañante, con estadía que {@code ocupa_cupo}. Los ocupados
 * de todas las inscripciones activas se cuentan en la base ({@code InscripcionParticipanteRepository.contarOcupados});
 * aquí solo se cuenta cuántos pide una reserva.
 */
public final class CalculadoraCupos {

	private CalculadoraCupos() {
	}

	/** Personas de la lista que se quedan en el páramo (las únicas que gastan cupo). */
	public static int personasParamo(List<String> estadias, CatalogoReglas catalogos) {
		return (int) estadias.stream().filter(catalogos::ocupaCupo).count();
	}

}
