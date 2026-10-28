package co.manaure.trekking.regla;

import java.time.LocalDate;

/**
 * Fechas en que se puede reservar hotel antes del evento: desde hoy (o la apertura) hasta la noche anterior
 * al primer día. La salida máxima es el primer día del evento.
 */
public record VentanaHotel(LocalDate primeraLlegada, LocalDate ultimaLlegada, LocalDate primeraSalida,
		LocalDate ultimaSalida, boolean disponible) {

	public static VentanaHotel de(LocalDate hoy, ParametrosEvento evento) {
		LocalDate ultimaSalida = evento.fechaEventoInicio();
		LocalDate primeraLlegada = evento.primerDiaElegible(hoy);
		LocalDate ultimaLlegada = ultimaSalida.minusDays(1);
		return new VentanaHotel(primeraLlegada, ultimaLlegada, primeraLlegada.plusDays(1), ultimaSalida,
				!primeraLlegada.isAfter(ultimaLlegada));
	}

}
