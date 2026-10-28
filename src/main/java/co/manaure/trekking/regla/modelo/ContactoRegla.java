package co.manaure.trekking.regla.modelo;

/** Contacto de emergencia. */
public record ContactoRegla(String nombre, Telefono telefono, String parentesco) {

	@Override
	public String toString() {
		return "ContactoRegla[***]";
	}

}
