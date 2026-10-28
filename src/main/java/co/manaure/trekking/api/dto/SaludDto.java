package co.manaure.trekking.api.dto;

/**
 * Salud tal como la devuelve la API (diseno §6.1: illness, allergies, disability, exercises). Detalle ausente
 * es null. El {@code toString()} no muestra valores para que no lleguen a los logs.
 */
public record SaludDto(String illness, String allergies, String disability, boolean exercises) {

	@Override
	public String toString() {
		return "SaludDto[datos de salud omitidos]";
	}

}
