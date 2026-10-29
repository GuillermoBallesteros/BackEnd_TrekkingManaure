package co.manaure.trekking.notificacion;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import co.manaure.trekking.pago.ConceptoPago;

/**
 * Textos de los parámetros de las plantillas, como los muestra el front: montos {@code $350.000}, fechas
 * {@code 9 de noviembre} y nombres con mayúscula inicial (en la base están en mayúsculas y sin tildes).
 */
public final class FormatoAviso {

	private static final Locale ES_CO = Locale.forLanguageTag("es-CO");
	private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d 'de' MMMM", ES_CO);

	private FormatoAviso() {
	}

	/** "ANA MARIA" → "Ana Maria"; la Ñ se conserva ("PEÑA" → "Peña"). */
	public static String nombrePropio(String texto) {
		if (texto == null || texto.isBlank()) {
			return "";
		}
		return Arrays.stream(texto.strip().split("\\s+"))
				.map(palabra -> palabra.substring(0, 1).toUpperCase(ES_CO) + palabra.substring(1).toLowerCase(ES_CO))
				.collect(Collectors.joining(" "));
	}

	/** Pesos enteros con separador de miles: 350000 → "$350.000". */
	public static String dinero(long pesos) {
		NumberFormat formato = NumberFormat.getIntegerInstance(ES_CO);
		formato.setGroupingUsed(true);
		return "$" + formato.format(pesos);
	}

	/** 2026-11-09 → "9 de noviembre"; sin fecha, texto vacío. */
	public static String fecha(LocalDate fecha) {
		return fecha == null ? "" : FECHA.format(fecha);
	}

	/** Texto del concepto como lo muestra el front. */
	public static String concepto(ConceptoPago concepto) {
		return switch (concepto) {
			case SEPARACION_CUPO -> "Separación de cupo";
			case PAGO_TOTAL -> "Pago total";
			case PAGO_SALDO -> "Pago de saldo";
		};
	}

}
