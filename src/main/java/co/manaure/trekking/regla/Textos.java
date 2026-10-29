package co.manaure.trekking.regla;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Expresiones y utilidades de texto compartidas por los validadores (mismas expresiones que el front). Los patrones
 * se aplican sobre el texto ya normalizado por {@link Normalizador}: nombres en MAYÚSCULAS y sin tildes, y lugares
 * sin tildes. La Ñ es una letra aparte y se conserva.
 */
public final class Textos {

	/** Largos máximos (sobre el texto normalizado). */
	public static final int NOMBRE_MIN = 2;
	public static final int NOMBRE_MAX = 40;
	public static final int CONTACTO_MIN = 5;
	public static final int CONTACTO_MAX = 80;
	public static final int LUGAR_MIN = 3;
	public static final int LUGAR_MAX = 40;
	public static final int CORREO_MAX = 100;

	private static final String LETRAS_NOMBRE = "A-ZÑ";
	private static final String LETRAS_LUGAR = "A-Za-zÑñ";
	/** Palabras de letras separadas por un espacio, guion o apóstrofe: MARIA JOSE, D'ANGELO. */
	private static final Pattern NOMBRE = Pattern
			.compile("[" + LETRAS_NOMBRE + "]+(?:[ '\\-][" + LETRAS_NOMBRE + "]+)*");
	/** Una palabra de un lugar: admite abreviaturas con punto (Sta., D.C.). */
	private static final String PALABRA_LUGAR = "[" + LETRAS_LUGAR + "]+(?:\\.[" + LETRAS_LUGAR + "]+)*\\.?";
	/** Como el nombre, pero con las mayúsculas escritas y abreviaturas con punto: Sta. Marta, Bogota D.C. */
	private static final Pattern LUGAR = Pattern.compile(PALABRA_LUGAR + "(?:[ '\\-]{1,2}" + PALABRA_LUGAR + ")*");
	/** Nombre de la EPS "Otra": solo letras y espacios simples. */
	private static final Pattern EPS_OTRA = Pattern
			.compile("[" + LETRAS_LUGAR + "]+(?: [" + LETRAS_LUGAR + "]+)*");
	private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

	/** Aviso del 409 público de documento duplicado: puede ser una reserva falsa a nombre de otra persona (H4). */
	public static final String AYUDA_DOCUMENTO_AJENO =
			" Si no fuiste tú, comunícate con la organización para que la revisen.";

	private Textos() {
	}

	public static String nz(String valor) {
		return valor == null ? "" : valor;
	}

	/** Nombre normalizado de {@code min} a {@code max} caracteres formado solo por letras A-Z, Ñ y separadores simples. */
	public static boolean nombreValido(String valor, int min, int max) {
		return valor != null && valor.length() >= min && valor.length() <= max && NOMBRE.matcher(valor).matches();
	}

	/** Nombre o apellido: 2 a 40 caracteres. */
	public static boolean nombreValido(String valor) {
		return nombreValido(valor, NOMBRE_MIN, NOMBRE_MAX);
	}

	/** Lugar (ciudad): de 3 a 40 caracteres, letras, Ñ, punto, apóstrofe, guion y espacio. */
	public static boolean lugarValido(String valor) {
		return valor != null && valor.length() >= LUGAR_MIN && valor.length() <= LUGAR_MAX
				&& LUGAR.matcher(valor).matches();
	}

	/** Nombre de la EPS "Otra": de 3 a 40 caracteres, solo letras y espacios. */
	public static boolean epsOtraValida(String valor) {
		return valor != null && valor.length() >= LUGAR_MIN && valor.length() <= LUGAR_MAX
				&& EPS_OTRA.matcher(valor).matches();
	}

	/** Documento comparable: sin espacios y en mayúsculas. */
	public static String normalizarDocumento(String valor) {
		return ESPACIOS.matcher(nz(valor)).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/**
	 * Nombre comparable: en minúsculas y con espacios simples. Se compara el nombre ya normalizado, así que la Ñ se
	 * respeta: PEÑA no es lo mismo que PENA.
	 */
	public static String nombreComparable(String valor) {
		String nfc = Normalizer.normalize(nz(valor), Normalizer.Form.NFC);
		return ESPACIOS.matcher(nfc.toLowerCase(Locale.ROOT)).replaceAll(" ").strip();
	}

	public static String nombreCompletoComparable(String nombres, String apellidos) {
		return nombreComparable(nz(nombres) + " " + nz(apellidos));
	}

}
