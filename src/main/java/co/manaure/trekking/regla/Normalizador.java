package co.manaure.trekking.regla;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

import co.manaure.trekking.regla.modelo.Telefono;

/**
 * Normalización de los textos del formulario, que se aplica ANTES de validar y de guardar (mismas funciones que
 * {@code shared/reservas/utils/normalizacion.ts} del front). Las longitudes y los caracteres permitidos se miden
 * sobre el resultado. Todas devuelven {@code null} si reciben {@code null}.
 *
 * <p>
 * Las tildes se aceptan al escribir y se quitan al guardar: cualquier marca latina (Á, è, ã, ç, ü...) se elimina,
 * salvo la tilde de la Ñ, que es otra letra (PEÑA no es PENA). Los detalles de salud no se normalizan.
 */
public final class Normalizador {

	private static final Pattern ESPACIOS = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
	private static final Pattern SEPARADORES_DOCUMENTO = Pattern.compile("[.\\-\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
	/** Apóstrofes curvos (el teclado del celular los pone solos) que pasan a {@code '}. */
	private static final Pattern APOSTROFES = Pattern.compile("[\\u2018\\u2019\\u02BC]");
	/** Guiones tipográficos que pasan a {@code -}. */
	private static final Pattern GUIONES = Pattern.compile("[\\u2010\\u2011]");
	private static final char TILDE_COMBINANTE = '̃';

	private Normalizador() {
	}

	/** Todo texto: sin espacios al inicio y al final, espacios dobles a uno y Unicode NFC. */
	public static String texto(String valor) {
		if (valor == null) {
			return null;
		}
		String nfc = Normalizer.normalize(valor, Normalizer.Form.NFC);
		return ESPACIOS.matcher(nfc).replaceAll(" ").strip();
	}

	/** Nombres, apellidos y nombre del contacto: MAYÚSCULAS y sin tildes (la Ñ se conserva). */
	public static String nombre(String valor) {
		String limpio = lugar(valor);
		return limpio == null ? null : limpio.toUpperCase(Locale.ROOT);
	}

	/** Ciudad y nombre de la EPS "Otra": sin tildes (la Ñ se conserva) y con las mayúsculas que escribió la persona. */
	public static String lugar(String valor) {
		String limpio = texto(valor);
		if (limpio == null) {
			return null;
		}
		limpio = GUIONES.matcher(APOSTROFES.matcher(limpio).replaceAll("'")).replaceAll("-");
		return sinTildes(limpio);
	}

	/** Documento: sin puntos, espacios ni guiones y en MAYÚSCULAS (1.098.726.541 pasa a 1098726541). */
	public static String documento(String valor) {
		if (valor == null) {
			return null;
		}
		return SEPARADORES_DOCUMENTO.matcher(valor).replaceAll("").toUpperCase(Locale.ROOT);
	}

	/** Correo: en minúsculas y sin espacios. */
	public static String correo(String valor) {
		if (valor == null) {
			return null;
		}
		return ESPACIOS.matcher(Normalizer.normalize(valor, Normalizer.Form.NFC)).replaceAll("")
				.toLowerCase(Locale.ROOT);
	}

	/** Teléfono: solo dígitos y, si traía el indicativo del país delante, sin él. */
	public static Telefono telefono(Telefono telefono, CatalogoReglas catalogos) {
		if (telefono == null) {
			return null;
		}
		String numero = telefono.numero() == null ? null
				: ValidadorTelefono.normalizarNumero(telefono.pais(), telefono.numero(), catalogos);
		return new Telefono(telefono.pais(), numero);
	}

	/** Quita las marcas de acento y conserva la tilde de la Ñ. */
	static String sinTildes(String valor) {
		String descompuesto = Normalizer.normalize(valor, Normalizer.Form.NFD);
		StringBuilder sb = new StringBuilder(descompuesto.length());
		for (int i = 0; i < descompuesto.length(); i++) {
			char c = descompuesto.charAt(i);
			if (esMarca(c) && !(c == TILDE_COMBINANTE && sb.length() > 0 && esN(sb.charAt(sb.length() - 1)))) {
				continue;
			}
			sb.append(c);
		}
		return Normalizer.normalize(sb, Normalizer.Form.NFC);
	}

	private static boolean esMarca(char c) {
		int tipo = Character.getType(c);
		return tipo == Character.NON_SPACING_MARK || tipo == Character.COMBINING_SPACING_MARK
				|| tipo == Character.ENCLOSING_MARK;
	}

	private static boolean esN(char c) {
		return c == 'N' || c == 'n';
	}

}
