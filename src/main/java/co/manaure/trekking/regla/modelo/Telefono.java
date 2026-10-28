package co.manaure.trekking.regla.modelo;

/** Teléfono: país (iso) y número nacional. */
public record Telefono(String pais, String numero) {

	@Override
	public String toString() {
		return "Telefono[***]";
	}

}
