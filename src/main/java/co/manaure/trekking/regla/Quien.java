package co.manaure.trekking.regla;

/** Cómo se nombra a cada persona en los mensajes y bajo qué prefijo se reporta el campo. */
public record Quien(String de, String sujeto, String prefijo) {

	public static final Quien TITULAR = new Quien("del participante", "El participante", "");
	public static final Quien ACOMPANANTE = new Quien("del acompañante", "El acompañante", "companion.");

	/** Ruta completa del campo: {@code companion.} + ruta para el acompañante. */
	public String campo(String ruta) {
		return prefijo + ruta;
	}

}
