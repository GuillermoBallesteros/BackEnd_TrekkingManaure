package co.manaure.trekking.seguridad;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Port de {@code validarCorreo}, {@code validarClave} y {@code nombreDesdeCorreo} del panel
 * (admin/src/utils/auth/validaciones.ts), con los mismos textos.
 */
public final class ValidadorCredenciales {

	public static final int CLAVE_MIN = 8;
	public static final int CLAVE_MAX = 128;
	public static final int CORREO_MAX = 254;

	private static final int FLAGS = Pattern.UNICODE_CHARACTER_CLASS;
	private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$", FLAGS);
	private static final Pattern ESPACIOS_EXTREMOS = Pattern.compile("^[\\s\\uFEFF]+|[\\s\\uFEFF]+$", FLAGS);
	private static final Pattern SEPARADORES = Pattern.compile("[._-]+");

	private ValidadorCredenciales() {
	}

	/** Equivalente a {@code String.prototype.trim} de JavaScript. */
	public static String recortar(String valor) {
		return ESPACIOS_EXTREMOS.matcher(valor).replaceAll("");
	}

	public static Optional<String> validarCorreo(String valor) {
		String correo = recortar(valor == null ? "" : valor);
		if (correo.isEmpty()) {
			return Optional.of("Escribe tu correo.");
		}
		if (correo.length() > CORREO_MAX || !CORREO.matcher(correo).matches()) {
			return Optional.of("Escribe un correo válido, por ejemplo nombre@dominio.com.");
		}
		return Optional.empty();
	}

	public static Optional<String> validarClave(String valor) {
		if (valor == null || valor.isEmpty()) {
			return Optional.of("Escribe tu contraseña.");
		}
		if (!recortar(valor).equals(valor)) {
			return Optional.of("La contraseña no puede empezar ni terminar con espacios.");
		}
		if (valor.length() < CLAVE_MIN) {
			return Optional.of("La contraseña debe tener al menos " + CLAVE_MIN + " caracteres.");
		}
		if (valor.length() > CLAVE_MAX) {
			return Optional.of("La contraseña no puede superar " + CLAVE_MAX + " caracteres.");
		}
		return Optional.empty();
	}

	/** Errores por campo ({@code correo}, {@code clave}); vacío si el formato es válido. */
	public static Map<String, String> validar(String correo, String clave) {
		Map<String, String> errores = new LinkedHashMap<>();
		validarCorreo(correo).ifPresent(m -> errores.put("correo", m));
		validarClave(clave).ifPresent(m -> errores.put("clave", m));
		return errores;
	}

	/** "maria.perez@..." pasa a "Maria Perez". */
	public static String nombreDesdeCorreo(String correo) {
		String local = recortar(correo).split("@", -1)[0];
		StringBuilder nombre = new StringBuilder();
		for (String parte : SEPARADORES.split(local)) {
			if (parte.isEmpty()) {
				continue;
			}
			if (nombre.length() > 0) {
				nombre.append(' ');
			}
			nombre.append(parte.substring(0, 1).toUpperCase(java.util.Locale.ROOT))
					.append(parte.substring(1).toLowerCase(java.util.Locale.ROOT));
		}
		return nombre.toString();
	}

}
