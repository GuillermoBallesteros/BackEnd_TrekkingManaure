package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.manaure.trekking.pago.ConceptoPago;
import co.manaure.trekking.regla.CalculadoraPrecios.Seleccion;
import co.manaure.trekking.regla.modelo.HotelSolicitado;
import co.manaure.trekking.reserva.PlanPago;

class CalculadoraPreciosTest {

	private static final HotelSolicitado DOS_NOCHES = new HotelSolicitado("2026-11-12", "2026-11-14", false);
	private static final HotelSolicitado DOS_NOCHES_CON = new HotelSolicitado("2026-11-12", "2026-11-14", true);

	private static long total(Seleccion s) {
		return CalculadoraPrecios.total(s, Datos.PRECIOS);
	}

	@Test
	void titularSoloEs350000() {
		assertThat(total(new Seleccion(false, false, List.of(), null))).isEqualTo(350_000);
	}

	@Test
	void acompananteSinKitEs500000YConKitEs700000() {
		assertThat(total(new Seleccion(true, false, List.of(), null))).isEqualTo(500_000);
		assertThat(total(new Seleccion(true, true, List.of(), null))).isEqualTo(700_000);
	}

	@ParameterizedTest
	@CsvSource({ "cuatrimoto,500000", "parapente,550000" })
	void extrasSumanSuPrecio(String extra, long esperado) {
		assertThat(total(new Seleccion(false, false, List.of(extra), null))).isEqualTo(esperado);
	}

	@Test
	void parapenteDelAcompananteSuma200000() {
		assertThat(total(new Seleccion(true, false, List.of("parapenteAcompanante"), null))).isEqualTo(700_000);
	}

	@Test
	void hotelDosNochesUnaPersonaEs90000() {
		assertThat(CalculadoraPrecios.cargoHotel(new Seleccion(false, false, List.of(), DOS_NOCHES), Datos.PRECIOS)
				.orElseThrow().monto()).isEqualTo(90_000);
	}

	@Test
	void hotelConAcompananteEs180000() {
		assertThat(CalculadoraPrecios.cargoHotel(new Seleccion(true, false, List.of(), DOS_NOCHES_CON),
				Datos.PRECIOS).orElseThrow().monto()).isEqualTo(180_000);
	}

	@Test
	void hotelConAcompananteSinAcompananteCuentaUnaPersona() {
		var cargo = CalculadoraPrecios.cargoHotel(new Seleccion(false, false, List.of(), DOS_NOCHES_CON),
				Datos.PRECIOS).orElseThrow();
		assertThat(cargo.monto()).isEqualTo(90_000);
		assertThat(cargo.etiqueta()).isEqualTo("Hotel antes del evento · 2 noches × 1 persona");
	}

	@Test
	void sinHotelNoHayCargo() {
		assertThat(CalculadoraPrecios.cargoHotel(new Seleccion(false, false, List.of(), null), Datos.PRECIOS))
				.isEmpty();
	}

	@Test
	void ejemplo3DelFlujoEs1100000() {
		assertThat(total(new Seleccion(true, true, List.of("parapente", "parapenteAcompanante"), null)))
				.isEqualTo(1_100_000);
	}

	@Test
	void ejemplo2DelFlujoEs650000ConAhora50000() {
		var s = new Seleccion(true, false, List.of("cuatrimoto"), null);
		var c = CalculadoraPrecios.cotizar(s, PlanPago.APARTADO, Datos.PRECIOS);
		assertThat(c.total()).isEqualTo(650_000);
		assertThat(c.ahora()).isEqualTo(50_000);
		assertThat(c.saldo()).isEqualTo(600_000);
		assertThat(c.extras()).isEqualTo(150_000);
	}

	@Test
	void ejemplo4DelFlujoTitularConHotelEs440000YConAcompananteEs680000() {
		assertThat(total(new Seleccion(false, false, List.of(), DOS_NOCHES))).isEqualTo(440_000);
		assertThat(total(new Seleccion(true, false, List.of(), DOS_NOCHES_CON))).isEqualTo(680_000);
	}

	@ParameterizedTest
	@CsvSource({ "350000,50000", "30000,30000", "50000,50000", "1100000,50000" })
	void apartadoPagaElMenorEntreApartadoYTotalComoSeparacion(long total, long esperado) {
		var pago = CalculadoraPrecios.pagoInicial(PlanPago.APARTADO, total, Datos.PRECIOS);
		assertThat(pago.concepto()).isEqualTo(ConceptoPago.SEPARACION_CUPO);
		assertThat(pago.monto()).isEqualTo(esperado);
	}

	@Test
	void totalPagaTodoComoPagoTotal() {
		var pago = CalculadoraPrecios.pagoInicial(PlanPago.TOTAL, 650_000, Datos.PRECIOS);
		assertThat(pago.concepto()).isEqualTo(ConceptoPago.PAGO_TOTAL);
		assertThat(pago.monto()).isEqualTo(650_000);
	}

	@Test
	void cargosTienenLasEtiquetasDelFront() {
		var cargos = CalculadoraPrecios.cargos(
				new Seleccion(true, true, List.of("cuatrimoto", "parapente", "parapenteAcompanante"), null),
				Datos.PRECIOS);
		assertThat(cargos).extracting(CalculadoraPrecios.Cargo::etiqueta).containsExactly(
				"Inscripción y kit del titular", "Acompañante con kit", "Paseo en cuatrimoto",
				"Vuelo en parapente del titular", "Vuelo en parapente del acompañante");
	}

}
