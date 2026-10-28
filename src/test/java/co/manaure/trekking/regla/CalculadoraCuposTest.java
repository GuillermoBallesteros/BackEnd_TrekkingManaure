package co.manaure.trekking.regla;

import static co.manaure.trekking.pago.RevisionPago.APROBADO;
import static co.manaure.trekking.pago.RevisionPago.POR_REVISAR;
import static co.manaure.trekking.pago.RevisionPago.RECHAZADO;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.manaure.trekking.regla.CalculadoraCupos.InscripcionCupo;
import co.manaure.trekking.regla.modelo.PagoRegla;

class CalculadoraCuposTest {

	private static final List<String> DOS_EN_PARAMO = List.of("Páramo", "Páramo");
	private static final CatalogoReglas CAT = Datos.CATALOGOS;

	private static PagoRegla p(co.manaure.trekking.pago.RevisionPago r) {
		return new PagoRegla(50_000, r);
	}

	@Test
	void soloCuentanPersonasConEstadiaParamoDeInscripcionesConPagoAprobado() {
		var inscripciones = List.of(
				new InscripcionCupo(List.of("Páramo", "Hotel en Manaure"), List.of(p(APROBADO))),
				new InscripcionCupo(DOS_EN_PARAMO, List.of(p(APROBADO), p(POR_REVISAR))),
				new InscripcionCupo(List.of("Regreso a casa"), List.of(p(APROBADO))));
		assertThat(CalculadoraCupos.ocupados(inscripciones, CAT)).isEqualTo(3);
	}

	@Test
	void inscripcionSoloPorRevisarOcupaCero() {
		assertThat(CalculadoraCupos.ocupados(List.of(new InscripcionCupo(DOS_EN_PARAMO, List.of(p(POR_REVISAR)))),
				CAT)).isZero();
	}

	@Test
	void inscripcionRechazadaOSinPagosOcupaCero() {
		var inscripciones = List.of(new InscripcionCupo(DOS_EN_PARAMO, List.of(p(RECHAZADO))),
				new InscripcionCupo(DOS_EN_PARAMO, List.of()));
		assertThat(CalculadoraCupos.ocupados(inscripciones, CAT)).isZero();
	}

	@ParameterizedTest
	@CsvSource({ "60,12,48", "10,12,0", "60,0,60" })
	void disponiblesNuncaSonNegativos(int capacidad, int ocupados, int disponibles) {
		var inscripciones = List.of(new InscripcionCupo(
				java.util.Collections.nCopies(ocupados, "Páramo"), List.of(p(APROBADO))));
		var d = CalculadoraCupos.disponibilidad(inscripciones, capacidad, CAT);
		assertThat(d.ocupados()).isEqualTo(ocupados);
		assertThat(d.disponibles()).isEqualTo(disponibles);
	}

	@ParameterizedTest
	@CsvSource({ "0,1", "1,1", "5,5" })
	void capacidadMinimaEsMaxEntre1YOcupados(int ocupados, int minima) {
		assertThat(CalculadoraCupos.capacidadMinima(ocupados)).isEqualTo(minima);
	}

}
