package co.manaure.trekking.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import co.manaure.trekking.pago.ConceptoPago;

/** HU-N15: los parámetros de las plantillas se ven como en el front. */
class FormatoAvisoTest {

	@Test
	void elNombreNormalizadoPasaAMayusculaInicialYConservaLaEnie() {
		assertThat(FormatoAviso.nombrePropio("ANA MARIA")).isEqualTo("Ana Maria");
		assertThat(FormatoAviso.nombrePropio("  MUÑOZ   PEÑA ")).isEqualTo("Muñoz Peña");
		assertThat(FormatoAviso.nombrePropio(null)).isEmpty();
		assertThat(FormatoAviso.nombrePropio(" ")).isEmpty();
	}

	@Test
	void losMontosLlevanPesosYSeparadorDeMiles() {
		assertThat(FormatoAviso.dinero(350000)).isEqualTo("$350.000");
		assertThat(FormatoAviso.dinero(1250000)).isEqualTo("$1.250.000");
		assertThat(FormatoAviso.dinero(0)).isEqualTo("$0");
	}

	@Test
	void lasFechasVanEnEspanol() {
		assertThat(FormatoAviso.fecha(LocalDate.of(2026, 11, 9))).isEqualTo("9 de noviembre");
		assertThat(FormatoAviso.fecha(LocalDate.of(2026, 11, 15))).isEqualTo("15 de noviembre");
		assertThat(FormatoAviso.fecha(null)).isEmpty();
	}

	@Test
	void losConceptosUsanLosTextosDelFront() {
		assertThat(FormatoAviso.concepto(ConceptoPago.SEPARACION_CUPO)).isEqualTo("Separación de cupo");
		assertThat(FormatoAviso.concepto(ConceptoPago.PAGO_TOTAL)).isEqualTo("Pago total");
		assertThat(FormatoAviso.concepto(ConceptoPago.PAGO_SALDO)).isEqualTo("Pago de saldo");
	}

}
