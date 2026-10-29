package co.manaure.trekking.regla;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import co.manaure.trekking.comun.error.CodigoError;

/** Correo electrónico con detección de dominios mal escritos (port de {@code validateEmail}). */
public final class ValidadorCorreo {

	private static final Pattern CORREO = Pattern.compile(
			"[a-z0-9](?:[a-z0-9._%+-]*[a-z0-9])?@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*\\.[a-z]{2,}");

	/** Dominios mal escritos y el dominio correcto. */
	static final Map<String, String> DOMINIOS_ERRADOS = Map.ofEntries(
			Map.entry("gmial.com", "gmail.com"), Map.entry("gmai.com", "gmail.com"),
			Map.entry("gamil.com", "gmail.com"), Map.entry("gmal.com", "gmail.com"),
			Map.entry("gmail.co", "gmail.com"), Map.entry("gmail.con", "gmail.com"),
			Map.entry("gmail.cm", "gmail.com"), Map.entry("hotmial.com", "hotmail.com"),
			Map.entry("hotmai.com", "hotmail.com"), Map.entry("hotmal.com", "hotmail.com"),
			Map.entry("hotmail.co", "hotmail.com"), Map.entry("hotmail.con", "hotmail.com"),
			Map.entry("yaho.com", "yahoo.com"), Map.entry("yahoo.co", "yahoo.com"),
			Map.entry("yahoo.con", "yahoo.com"), Map.entry("outlok.com", "outlook.com"),
			Map.entry("outlook.co", "outlook.com"), Map.entry("outlook.con", "outlook.com"));

	private ValidadorCorreo() {
	}

	/** El servidor pasa el correo a minúsculas y sin espacios antes de validar (diseño §8). */
	public static String normalizar(String correo) {
		return Normalizador.correo(correo);
	}

	/** {@code quien}: "del participante"; {@code campo}: ruta del correo ({@code email}, {@code companion.email}). */
	public static Optional<ErrorRegla> validar(String correo, String quien, String campo) {
		String email = Textos.nz(correo);
		if (email.isEmpty()) {
			return ErrorRegla.de(CodigoError.CORREO_INVALIDO, "Escribe el correo electrónico " + quien + ".", campo);
		}
		if (email.length() > Textos.CORREO_MAX || email.contains("..") || !CORREO.matcher(email).matches()) {
			return ErrorRegla.de(CodigoError.CORREO_INVALIDO, "Revisa el correo electrónico " + quien + ".", campo);
		}
		String dominio = email.substring(email.indexOf('@') + 1);
		String sugerencia = DOMINIOS_ERRADOS.get(dominio);
		return sugerencia == null ? Optional.empty()
				: ErrorRegla.de(CodigoError.CORREO_INVALIDO,
						"El correo " + quien + " parece mal escrito: ¿quisiste decir @" + sugerencia + "?", campo);
	}

}
