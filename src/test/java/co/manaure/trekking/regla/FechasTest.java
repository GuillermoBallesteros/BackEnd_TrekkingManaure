package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class FechasTest {

	@ParameterizedTest
	@ValueSource(strings = { "2026-10-28", "2024-02-29", "0001-01-01" })
	void fechasIsoValidas(String valor) {
		assertThat(Fechas.esFechaIso(valor)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "2026-02-30", "2026-13-01", "2026-1-01", "26-10-28", "2026-10-28\n", "abc",
			"2026-10-28T10:00:00Z", "2026/10/28", "2026-10-28 " })
	void fechasIsoInvalidas(String valor) {
		assertThat(Fechas.esFechaIso(valor)).isFalse();
	}

	@Test
	void nuloNoEsFecha() {
		assertThat(Fechas.esFechaIso(null)).isFalse();
	}

	@ParameterizedTest
	@CsvSource({ "2009-10-28,2026-10-28,17", "2008-10-28,2026-10-28,18", "2008-10-29,2026-10-28,17",
			"2000-02-29,2001-02-28,0", "2000-02-29,2001-03-01,1", "1990-05-14,2026-10-28,36" })
	void edadEnAnosCumplidos(String nacimiento, String hoy, int esperada) {
		assertThat(Fechas.edad(LocalDate.parse(nacimiento), LocalDate.parse(hoy))).isEqualTo(esperada);
	}

	@Test
	void menorEsMenorDe18ConFechaValida() {
		assertThat(Fechas.esMenor("2009-10-29", Datos.HOY)).isTrue();
		assertThat(Fechas.esMenor("2008-10-28", Datos.HOY)).isFalse();
		assertThat(Fechas.esMenor("basura", Datos.HOY)).isFalse();
	}

	@Test
	void formatearUsaDiaYMesEnEspanol() {
		assertThat(Fechas.formatear(LocalDate.parse("2026-11-09"))).isEqualTo("9 de noviembre");
		assertThat(Fechas.formatear(LocalDate.parse("2026-10-28"))).isEqualTo("28 de octubre");
	}

	@Test
	void ventanaHotelConHoyFijo() {
		var v = VentanaHotel.de(Datos.HOY, Datos.EVENTO);
		assertThat(v.primeraLlegada()).isEqualTo("2026-10-28");
		assertThat(v.ultimaLlegada()).isEqualTo("2026-11-14");
		assertThat(v.primeraSalida()).isEqualTo("2026-10-29");
		assertThat(v.ultimaSalida()).isEqualTo("2026-11-15");
		assertThat(v.disponible()).isTrue();
	}

	@Test
	void ventanaHotelUsaLaAperturaSiHoyEsAnterior() {
		assertThat(VentanaHotel.de(LocalDate.parse("2026-10-01"), Datos.EVENTO).primeraLlegada())
				.isEqualTo("2026-10-28");
	}

	@Test
	void ventanaHotelNoDisponibleElDiaDelEvento() {
		assertThat(VentanaHotel.de(LocalDate.parse("2026-11-15"), Datos.EVENTO).disponible()).isFalse();
	}

}
