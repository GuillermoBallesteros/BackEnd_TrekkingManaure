package co.manaure.trekking.regla;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import co.manaure.trekking.pago.ConceptoPago;
import co.manaure.trekking.regla.modelo.HotelSolicitado;
import co.manaure.trekking.regla.modelo.NuevaReserva;
import co.manaure.trekking.reserva.PlanPago;

/** Único cálculo de precios del sistema (port de {@code cotizacion.ts}). Dinero en pesos enteros (long). */
public final class CalculadoraPrecios {

	/** Lo que define el precio: acompañante (con o sin kit), extras y hotel previo. */
	public record Seleccion(boolean conAcompanante, boolean acompananteConKit, List<String> extras,
			HotelSolicitado hotel) {

		public static Seleccion de(NuevaReserva reserva) {
			boolean hay = reserva.acompanante() != null;
			return new Seleccion(hay, hay && reserva.acompanante().kit(), reserva.extras(), reserva.hotel());
		}
	}

	public record Cargo(String etiqueta, long monto) {
	}

	public record PagoInicial(ConceptoPago concepto, long monto) {
	}

	/** Cotización del formulario y del resumen: total, lo que se paga ahora y el restante. */
	public record Cotizacion(long titular, long acompanante, long extras, long hotel, long total, long ahora,
			long saldo) {
	}

	private CalculadoraPrecios() {
	}

	public static long precioAcompanante(boolean conKit, PreciosVigentes precios) {
		return conKit ? precios.acompananteConKit() : precios.acompananteSinKit();
	}

	/** La cuatrimoto vale lo mismo con o sin acompañante; el parapente se cobra por persona. */
	public static long precioExtra(String extra, PreciosVigentes precios) {
		return "cuatrimoto".equals(extra) ? precios.cuatrimoto() : precios.parapente();
	}

	private static String etiquetaExtra(String extra) {
		return switch (extra) {
			case "cuatrimoto" -> "Paseo en cuatrimoto";
			case "parapente" -> "Vuelo en parapente del titular";
			case "parapenteAcompanante" -> "Vuelo en parapente del acompañante";
			default -> extra;
		};
	}

	/** Hotel previo: noches x personas x tarifa por noche; vacío si no lo pidió. */
	public static Optional<Cargo> cargoHotel(Seleccion seleccion, PreciosVigentes precios) {
		HotelSolicitado hotel = seleccion.hotel();
		if (hotel == null) {
			return Optional.empty();
		}
		long noches = ValidadorHotel.noches(hotel.llegada(), hotel.salida());
		int personas = ValidadorHotel.personas(hotel, seleccion.conAcompanante());
		String etiqueta = "Hotel antes del evento · " + noches + (noches == 1 ? " noche" : " noches") + " × "
				+ personas + (personas == 1 ? " persona" : " personas");
		return Optional.of(new Cargo(etiqueta, Math.multiplyExact(Math.multiplyExact(noches, personas),
				precios.hotelNoche())));
	}

	/** Titular (inscripción y kit), acompañante, extras y hotel. */
	public static List<Cargo> cargos(Seleccion seleccion, PreciosVigentes precios) {
		List<Cargo> cargos = new ArrayList<>();
		cargos.add(new Cargo("Inscripción y kit del titular", precios.titular()));
		if (seleccion.conAcompanante()) {
			cargos.add(new Cargo(seleccion.acompananteConKit() ? "Acompañante con kit" : "Acompañante sin kit",
					precioAcompanante(seleccion.acompananteConKit(), precios)));
		}
		if (seleccion.extras() != null) {
			for (String extra : seleccion.extras()) {
				cargos.add(new Cargo(etiquetaExtra(extra), precioExtra(extra, precios)));
			}
		}
		cargoHotel(seleccion, precios).ifPresent(cargos::add);
		return cargos;
	}

	public static long total(Seleccion seleccion, PreciosVigentes precios) {
		return cargos(seleccion, precios).stream().mapToLong(Cargo::monto).reduce(0L, Math::addExact);
	}

	/** Pago al reservar: apartado = min(valor del apartado, total) como separación; total = todo. */
	public static PagoInicial pagoInicial(PlanPago plan, long total, PreciosVigentes precios) {
		return plan == PlanPago.APARTADO
				? new PagoInicial(ConceptoPago.SEPARACION_CUPO, Math.min(precios.apartado(), total))
				: new PagoInicial(ConceptoPago.PAGO_TOTAL, total);
	}

	public static Cotizacion cotizar(Seleccion seleccion, PlanPago plan, PreciosVigentes precios) {
		long total = total(seleccion, precios);
		long acompanante = seleccion.conAcompanante()
				? precioAcompanante(seleccion.acompananteConKit(), precios)
				: 0;
		long hotel = cargoHotel(seleccion, precios).map(Cargo::monto).orElse(0L);
		long ahora = pagoInicial(plan, total, precios).monto();
		return new Cotizacion(precios.titular(), acompanante, total - precios.titular() - acompanante - hotel,
				hotel, total, ahora, total - ahora);
	}

}
