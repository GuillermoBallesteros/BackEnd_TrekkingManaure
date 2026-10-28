package co.manaure.trekking.regla;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Expresiones y utilidades de texto compartidas por los validadores (mismas expresiones que el front). */
public final class Textos {

	private static final String LETRAS = "A-Za-zÁÉÍÓÚÜÑáéíóúüñ";
	/** Palabras de letras separadas por un espacio, guion o apóstrofe: María José, D'Angelo. */
	private static final Pattern NOMBRE = Pattern.compile("[" + LETRAS + "]+(?:[ '\\-][" + LETRAS + "]+)*");
	/** Como el nombre, pero admite abreviaturas con punto: Sta. Marta. */
	private static final Pattern LUGAR = Pattern
			.compile("[" + LETRAS + "]+\\.?(?:[ '\\-]{1,2}[" + LETRAS + "]+\\.?)*");
	private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern MARCAS = Pattern.compile("[\\u0300-\\u036f]");

	/** Aviso del 409 público de documento duplicado: puede ser una reserva falsa a nombre de otra persona (H4). */
	public static final String AYUDA_DOCUMENTO_AJENO =
			" Si no fuiste tú, comunícate con la organización para que la revisen.";

	private Textos() {
	}

	public static String nz(String valor) {
		return valor == null ? "" : valor;
	}

	/** Nombre de 2 a 60 caracteres (por defecto) formado solo por letras y separadores simples. */
	public static boolean nombreValido(String valor, int min, int max) {
		return valor != null && valor.length() >= min && valor.length() <= max && NOMBRE.matcher(valor).matches();
	}

	public static boolean nombreValido(String valor) {
		return nombreValido(valor, 2, 60);
	}

	/** Lugar (ciudad, EPS "Otra"): como el nombre, con puntos de abreviatura. */
	public static boolean lugarValido(String valor) {
		return valor != null && valor.length() <= 200 && LUGAR.matcher(valor).matches();
	}

	/** Documento comparable: sin espacios y en mayúsculas. */
	public static String normalizarDocumento(String valor) {
		return ESPACIOS.matcher(nz(valor)).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/** Nombre comparable: sin tildes, en minúsculas y con espacios simples. */
	public static String nombreComparable(String valor) {
		String sinTildes = MARCAS.matcher(Normalizer.normalize(nz(valor), Normalizer.Form.NFD)).replaceAll("");
		return ESPACIOS.matcher(sinTildes.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
	}

	public static String nombreCompletoComparable(String nombres, String apellidos) {
		return nombreComparable(nz(nombres) + " " + nz(apellidos));
	}

}
