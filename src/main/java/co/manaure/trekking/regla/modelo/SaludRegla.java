package co.manaure.trekking.regla.modelo;

/** Ficha de salud: detalles nulos si no aplica; {@code ejercita} nulo es inválido. */
public record SaludRegla(String enfermedad, String alergias, String discapacidad, Boolean ejercita) {

	/** Sin valores: son datos de salud. */
	@Override
	public String toString() {
		return "SaludRegla[***]";
	}

}
