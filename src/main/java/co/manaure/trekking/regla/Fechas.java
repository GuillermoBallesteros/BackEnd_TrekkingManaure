package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.regex.Pattern;

/** Fechas AAAA-MM-DD y edad (port de {@code fechas.ts}). {@code hoy} siempre llega como parámetro. */
public final class Fechas {

	/** Edad desde la que una persona es mayor de edad en Colombia. */
	public static final int MAYORIA_EDAD = 18;
	/** Edad máxima que se acepta en una fecha de nacimiento. */
	public static final int EDAD_MAXIMA = 110;

	private static final Pattern ISO = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
	private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
			"agosto", "septiembre", "octubre", "noviembre", "diciembre" };

	private Fechas() {
	}

	/** Fecha si el texto es AAAA-MM-DD y existe en el calendario. */
	public static Optional<LocalDate> parsear(String valor) {
		if (valor == null || !ISO.matcher(valor).matches()) {
			return Optional.empty();
		}
		try {
			return Optional.of(LocalDate.parse(valor));
		} catch (DateTimeParseException e) {
			return Optional.empty();
		}
	}

	public static boolean esFechaIso(String valor) {
		return parsear(valor).isPresent();
	}

	public static long diasEntre(LocalDate desde, LocalDate hasta) {
		return ChronoUnit.DAYS.between(desde, hasta);
	}

	/** Años cumplidos a {@code hoy}. */
	public static int edad(LocalDate nacimiento, LocalDate hoy) {
		return Period.between(nacimiento, hoy).getYears();
	}

	/** Menor de edad a {@code hoy}; una fecha inválida no cuenta como menor. */
	public static boolean esMenor(String nacimiento, LocalDate hoy) {
		return parsear(nacimiento).map(n -> edad(n, hoy) < MAYORIA_EDAD).orElse(false);
	}

	/** "9 de noviembre", como {@code formatDate} del front (es-CO, sin año). */
	public static String formatear(LocalDate fecha) {
		return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1];
	}

}
