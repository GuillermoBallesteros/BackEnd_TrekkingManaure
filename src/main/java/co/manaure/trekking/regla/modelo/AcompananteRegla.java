package co.manaure.trekking.regla.modelo;

/** Acompañante: persona más kit y talla ({@code talla} nula si no lleva kit). */
public record AcompananteRegla(PersonaRegla persona, boolean kit, String talla) {

	@Override
	public String toString() {
		return "AcompananteRegla[***]";
	}

}
