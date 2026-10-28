package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.Optional;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.modelo.HotelSolicitado;

/** Hotel previo al evento y su ventana (port de {@code validatePreEventHotel}). */
public final class ValidadorHotel {

	private ValidadorHotel() {
	}

	/** Noches entre llegada y salida; 0 si alguna fecha es inválida o la salida no es posterior. */
	public static long noches(String llegada, String salida) {
		Optional<LocalDate> in = Fechas.parsear(llegada);
		Optional<LocalDate> out = Fechas.parsear(salida);
		if (in.isEmpty() || out.isEmpty()) {
			return 0;
		}
		return Math.max(0, Fechas.diasEntre(in.get(), out.get()));
	}

	/** Personas que llegan antes: el titular y, si viene y se suma, el acompañante. */
	public static int personas(HotelSolicitado hotel, boolean hayAcompanante) {
		return hayAcompanante && hotel.conAcompanante() ? 2 : 1;
	}

	public static Optional<ErrorRegla> validar(HotelSolicitado hotel, boolean hayAcompanante, LocalDate hoy,
			ParametrosEvento evento) {
		VentanaHotel ventana = VentanaHotel.de(hoy, evento);
		if (!ventana.disponible()) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO, "Ya no se puede reservar hotel antes del evento.",
					"preEventHotel");
		}
		Optional<LocalDate> llegada = Fechas.parsear(hotel.llegada());
		Optional<LocalDate> salida = Fechas.parsear(hotel.salida());
		if (llegada.isEmpty() || salida.isEmpty()) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO, "Elige las fechas de llegada y salida del hotel.",
					llegada.isEmpty() ? "preEventHotel.checkIn" : "preEventHotel.checkOut");
		}
		LocalDate in = llegada.get();
		LocalDate out = salida.get();
		if (in.isBefore(ventana.primeraLlegada()) || in.isAfter(ventana.ultimaLlegada())) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO,
					"La llegada al hotel debe estar entre el " + Fechas.formatear(ventana.primeraLlegada()) + " y el "
							+ Fechas.formatear(ventana.ultimaLlegada()) + ".",
					"preEventHotel.checkIn");
		}
		if (!out.isAfter(in)) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO, "La salida del hotel debe ser después de la llegada.",
					"preEventHotel.checkOut");
		}
		if (out.isAfter(ventana.ultimaSalida())) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO,
					"La salida del hotel no puede ser después del " + Fechas.formatear(ventana.ultimaSalida()) + ".",
					"preEventHotel.checkOut");
		}
		if (noches(hotel.llegada(), hotel.salida()) < 1) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO, "El hotel previo debe tener al menos una noche.",
					"preEventHotel.checkOut");
		}
		if (hotel.conAcompanante() && !hayAcompanante) {
			return ErrorRegla.de(CodigoError.HOTEL_INVALIDO,
					"Solo puedes sumar al acompañante al hotel si llevas acompañante.",
					"preEventHotel.withCompanion");
		}
		return Optional.empty();
	}

}
