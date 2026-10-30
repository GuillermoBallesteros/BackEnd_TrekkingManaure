package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CalculadoraCuposTest {

	private static final CatalogoReglas CAT = Datos.CATALOGOS;

	@Test
	void soloCuentanLasPersonasConEstadiaParamo() {
		assertThat(CalculadoraCupos.personasParamo(List.of("Páramo", "Hotel en Manaure"), CAT)).isEqualTo(1);
		assertThat(CalculadoraCupos.personasParamo(List.of("Páramo", "Páramo"), CAT)).isEqualTo(2);
		assertThat(CalculadoraCupos.personasParamo(List.of("Regreso a casa", "Hotel en Manaure"), CAT)).isZero();
	}

	@Test
	void sinPersonasNoPideCamas() {
		assertThat(CalculadoraCupos.personasParamo(List.of(), CAT)).isZero();
	}

}
