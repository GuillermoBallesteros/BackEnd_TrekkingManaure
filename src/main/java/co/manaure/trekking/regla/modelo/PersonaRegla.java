package co.manaure.trekking.regla.modelo;

/** Datos de una persona (titular o acompañante) tal como los valida el front ({@code Person}). */
public record PersonaRegla(String nombres, String apellidos, String tipoDocumento, String documento,
		String fechaNacimiento, String genero, Telefono telefono, String correo, String ciudad, EpsElegida eps,
		ContactoRegla contacto, SaludRegla salud, Compromisos compromisos, Consentimiento consentimiento,
		String estadia) {

	/** Sin valores: datos personales y de salud. */
	@Override
	public String toString() {
		return "PersonaRegla[***]";
	}

}
