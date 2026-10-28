package co.manaure.trekking.regla;

import static co.manaure.trekking.pago.RevisionPago.APROBADO;
import static co.manaure.trekking.pago.RevisionPago.POR_REVISAR;
import static co.manaure.trekking.pago.RevisionPago.RECHAZADO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import co.manaure.trekking.regla.modelo.PagoRegla;

class EstadoInscripcionTest {

	private static final LocalDate HOY = Datos.HOY;
	private static final long TOTAL = 500_000;

	private static PagoRegla p(long monto, co.manaure.trekking.pago.RevisionPago r) {
		return new PagoRegla(monto, r);
	}

	private static EstadoInscripcion estado(List<PagoRegla> pagos, String compromiso) {
		return EstadoInscripcion.derivar(TOTAL, pagos, compromiso == null ? null : LocalDate.parse(compromiso), HOY);
	}

	@Test
	void sinPagosEsPorVerificar() {
		assertThat(estado(List.of(), null)).isEqualTo(EstadoInscripcion.POR_VERIFICAR);
	}

	@Test
	void unPagoPorRevisarEsPorVerificar() {
		assertThat(estado(List.of(p(50_000, POR_REVISAR)), null)).isEqualTo(EstadoInscripcion.POR_VERIFICAR);
	}

	@Test
	void todosRechazadosEsRechazado() {
		var e = estado(List.of(p(50_000, RECHAZADO), p(100_000, RECHAZADO)), null);
		assertThat(e).isEqualTo(EstadoInscripcion.RECHAZADO);
		assertThat(e.activa()).isFalse();
	}

	@Test
	void rechazadoYPorRevisarSigueSiendoPorVerificar() {
		assertThat(estado(List.of(p(50_000, RECHAZADO), p(50_000, POR_REVISAR)), null))
				.isEqualTo(EstadoInscripcion.POR_VERIFICAR);
	}

	@Test
	void aprobadoParcialSinFechaEsSaldoPendiente() {
		assertThat(estado(List.of(p(50_000, APROBADO)), null)).isEqualTo(EstadoInscripcion.SALDO_PENDIENTE);
	}

	@Test
	void aprobadoParcialConFechaIgualOMayorAHoyEsSaldoPendiente() {
		assertThat(estado(List.of(p(50_000, APROBADO)), "2026-10-28")).isEqualTo(EstadoInscripcion.SALDO_PENDIENTE);
		assertThat(estado(List.of(p(50_000, APROBADO)), "2026-11-09")).isEqualTo(EstadoInscripcion.SALDO_PENDIENTE);
	}

	@Test
	void aprobadoParcialConFechaMenorAHoyEsVencido() {
		assertThat(estado(List.of(p(50_000, APROBADO)), "2026-10-27")).isEqualTo(EstadoInscripcion.VENCIDO);
	}

	@Test
	void aprobadoIgualOMayorAlTotalEsPagadoAunqueLaFechaPaso() {
		assertThat(estado(List.of(p(500_000, APROBADO)), "2026-10-01")).isEqualTo(EstadoInscripcion.PAGADO);
		assertThat(estado(List.of(p(300_000, APROBADO), p(250_000, APROBADO)), null))
				.isEqualTo(EstadoInscripcion.PAGADO);
	}

	@Test
	void aprobadoConOtroPorRevisarEsSaldoPendiente() {
		var e = estado(List.of(p(50_000, APROBADO), p(100_000, POR_REVISAR)), null);
		assertThat(e).isEqualTo(EstadoInscripcion.SALDO_PENDIENTE);
		assertThat(e.activa()).isTrue();
	}

	@Test
	void aprobadoConOtroRechazadoNoCuentaElRechazado() {
		assertThat(EstadoInscripcion.pagado(List.of(p(50_000, APROBADO), p(100_000, RECHAZADO)))).isEqualTo(50_000);
		assertThat(EstadoInscripcion.saldo(TOTAL, List.of(p(50_000, APROBADO), p(100_000, RECHAZADO))))
				.isEqualTo(450_000);
	}

	@Test
	void saldoNuncaEsNegativo() {
		assertThat(EstadoInscripcion.saldo(TOTAL, List.of(p(600_000, APROBADO)))).isZero();
	}

	@Test
	void etiquetasCoincidenConElFront() {
		assertThat(List.of(EstadoInscripcion.values())).extracting(EstadoInscripcion::etiqueta)
				.containsExactly("Por verificar", "Saldo pendiente", "Vencido", "Pagado", "Rechazado");
	}

}
