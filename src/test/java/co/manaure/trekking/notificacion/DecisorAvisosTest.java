package co.manaure.trekking.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.EstadoInscripcion;
import co.manaure.trekking.reserva.RolParticipante;

/** HU-N15: qué aviso toca cada día según el estado derivado y las fechas (RN-24). */
class DecisorAvisosTest {

	private static final LocalDate COMPROMISO = LocalDate.of(2026, 11, 9);
	private static final LocalDate INICIO_EVENTO = LocalDate.of(2026, 11, 15);
	private static final Set<RolParticipante> SOLO_TITULAR = Set.of(RolParticipante.TITULAR);
	private static final Set<RolParticipante> AMBOS = Set.of(RolParticipante.TITULAR, RolParticipante.ACOMPANANTE);

	private static List<Aviso> recordatorios(EstadoInscripcion estado, LocalDate hoy) {
		return DecisorAvisos.recordatorios(estado, COMPROMISO, INICIO_EVENTO, hoy, AMBOS);
	}

	@Test
	void saldoPendienteAvisa3Dias1DiaYElMismoDiaAlTitular() {
		assertThat(recordatorios(EstadoInscripcion.SALDO_PENDIENTE, LocalDate.of(2026, 11, 6)))
				.containsExactly(new Aviso(TipoAviso.PAGO_3_DIAS, "2026-11-09", RolParticipante.TITULAR));
		assertThat(recordatorios(EstadoInscripcion.SALDO_PENDIENTE, LocalDate.of(2026, 11, 8)))
				.containsExactly(new Aviso(TipoAviso.PAGO_1_DIA, "2026-11-09", RolParticipante.TITULAR));
		assertThat(recordatorios(EstadoInscripcion.SALDO_PENDIENTE, COMPROMISO))
				.containsExactly(new Aviso(TipoAviso.PAGO_HOY, "2026-11-09", RolParticipante.TITULAR));
	}

	@Test
	void losDemasDiasNoAvisan() {
		for (int dia : new int[] { 1, 4, 5, 7 }) {
			assertThat(recordatorios(EstadoInscripcion.SALDO_PENDIENTE, LocalDate.of(2026, 11, dia))).isEmpty();
		}
	}

	@Test
	void vencidoAvisaSoloElDiaSiguienteALaFechaCompromiso() {
		assertThat(recordatorios(EstadoInscripcion.VENCIDO, LocalDate.of(2026, 11, 10)))
				.containsExactly(new Aviso(TipoAviso.PAGO_VENCIDO, "2026-11-09", RolParticipante.TITULAR));
		assertThat(recordatorios(EstadoInscripcion.VENCIDO, LocalDate.of(2026, 11, 11))).isEmpty();
	}

	@Test
	void sinSeparacionAprobadaRechazadaOSinFechaCompromisoNoHayRecordatorioDeSaldo() {
		LocalDate hoy = LocalDate.of(2026, 11, 6);
		assertThat(recordatorios(EstadoInscripcion.POR_VERIFICAR, hoy)).isEmpty();
		assertThat(recordatorios(EstadoInscripcion.RECHAZADO, hoy)).isEmpty();
		assertThat(DecisorAvisos.recordatorios(EstadoInscripcion.SALDO_PENDIENTE, null, INICIO_EVENTO, hoy, AMBOS))
				.isEmpty();
	}

	@Test
	void pagadoNoRecibeRecordatoriosDeSaldo() {
		assertThat(recordatorios(EstadoInscripcion.PAGADO, LocalDate.of(2026, 11, 6))).isEmpty();
		assertThat(recordatorios(EstadoInscripcion.PAGADO, COMPROMISO)).isEmpty();
	}

	@Test
	void laVisperaDelEventoAvisaACadaPersonaDeUnaInscripcionPagada() {
		LocalDate vispera = LocalDate.of(2026, 11, 14);
		assertThat(DecisorAvisos.recordatorios(EstadoInscripcion.PAGADO, null, INICIO_EVENTO, vispera, AMBOS))
				.containsExactly(new Aviso(TipoAviso.EVENTO_MANANA, "2026-11-14/TITULAR", RolParticipante.TITULAR),
						new Aviso(TipoAviso.EVENTO_MANANA, "2026-11-14/ACOMPANANTE", RolParticipante.ACOMPANANTE));
		assertThat(DecisorAvisos.recordatorios(EstadoInscripcion.PAGADO, null, INICIO_EVENTO, vispera, SOLO_TITULAR))
				.containsExactly(new Aviso(TipoAviso.EVENTO_MANANA, "2026-11-14/TITULAR", RolParticipante.TITULAR));
	}

	@Test
	void laVisperaNoAvisaASaldoPendienteNiOtrosDias() {
		LocalDate vispera = LocalDate.of(2026, 11, 14);
		assertThat(DecisorAvisos.recordatorios(EstadoInscripcion.SALDO_PENDIENTE, null, INICIO_EVENTO, vispera, AMBOS))
				.isEmpty();
		assertThat(DecisorAvisos.recordatorios(EstadoInscripcion.PAGADO, null, INICIO_EVENTO, INICIO_EVENTO, AMBOS))
				.isEmpty();
	}

	@Test
	void unaRevisionAprobadaORechazadaAvisaUnaVezPorPagoYDevolverARevisionNo() {
		assertThat(DecisorAvisos.porRevision(RevisionPago.APROBADO, "PAG-0007"))
				.contains(new Aviso(TipoAviso.PAGO_APROBADO, "PAG-0007", RolParticipante.TITULAR));
		assertThat(DecisorAvisos.porRevision(RevisionPago.RECHAZADO, "PAG-0007"))
				.contains(new Aviso(TipoAviso.PAGO_RECHAZADO, "PAG-0007", RolParticipante.TITULAR));
		assertThat(DecisorAvisos.porRevision(RevisionPago.POR_REVISAR, "PAG-0007")).isEmpty();
	}

}
