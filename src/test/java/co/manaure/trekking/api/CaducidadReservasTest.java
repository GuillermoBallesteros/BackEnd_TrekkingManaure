package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.TestPropertySource;

import co.manaure.trekking.pago.CaducidadReservasService;
import co.manaure.trekking.pago.CaducidadReservasTask;

/** H4 con PostgreSQL real y reloj controlable: caducidad a 12 horas de los pagos sin verificar. */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "app.reservas.caducidad.lote=2")
class CaducidadReservasTest extends ReservaPublicaBase {

	private static final String MOTIVO = "No se verificó el pago dentro de 12 horas.";
	private static final Duration DOCE_HORAS = Duration.ofHours(12);

	@Autowired
	CaducidadReservasService caducidad;

	private void avanzar(Duration d) {
		reloj.fijar(reloj.instant().plus(d));
	}

	private Map<String, Object> pago(String codigo) {
		return jdbc.queryForMap("select * from pago where codigo = ?", codigo);
	}

	private Map<String, Object> inscripcion(String codigo) {
		return jdbc.queryForMap("select * from inscripcion where codigo = ?", codigo);
	}

	private int documentosActivos(String codigoInscripcion) {
		return jdbc.queryForObject("""
				select count(documento_activo) from inscripcion_participante
				where inscripcion_id = (select id from inscripcion where codigo = ?)""", Integer.class,
				codigoInscripcion);
	}

	private int auditoriasAutomaticas() {
		return jdbc.queryForObject("select count(*) from auditoria where accion = 'RECHAZO_AUTOMATICO'",
				Integer.class);
	}

	@Test
	void unPagoDe11Horas59MinutosNoSeTocaYUnoDe12HorasSeRechazaConElMotivoExacto() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());

		avanzar(DOCE_HORAS.minusMinutes(1));
		assertThat(caducidad.ejecutar()).isZero();
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);

		avanzar(Duration.ofMinutes(1));
		assertThat(caducidad.ejecutar()).isEqualTo(1);
		Map<String, Object> p = pago("PAG-0001");
		assertThat(p.get("revision")).isEqualTo("RECHAZADO");
		assertThat(p.get("motivo_rechazo")).isEqualTo(MOTIVO);
		assertThat(p.get("revisado_por")).isNull();
		assertThat(p.get("revisado_en")).isNotNull();
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);
		assertThat(documentosActivos("PER-0001")).isZero();
	}

	@Test
	void elDocumentoQuedaLibreYUnaNuevaReservaDa201() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);
		caducidad.ejecutar();
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
	}

	@Test
	void elPanelMuestraElMotivoDeRechazoAutomatico() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);
		caducidad.ejecutar();
		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.get("/api/v1/admin/reservas").header("Authorization", "Bearer " + tokenAdmin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.registrations[0].payments[0].review").value("Rechazado"))
				.andExpect(jsonPath("$.registrations[0].payments[0].rejectionReason").value(MOTIVO));
	}

	@Test
	void unaReservaConUnPagoAprobadoYOtroPendienteViejoNuncaCaduca() throws Exception {
		enviar(ReservaJson.conAcompanante().set("extras", List.of("cuatrimoto")).set("plan", "apartado")
				.set("commitmentDate", "2026-11-09")).andExpect(status().isCreated());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		jdbc.update("""
				insert into pago (codigo, inscripcion_id, concepto, monto, fecha, revision, origen, en_revision_desde)
				values ('PAG-0099', (select id from inscripcion), 'PAGO_SALDO', 600000, current_date, 'POR_REVISAR',
				'PUBLICO', now() - interval '3 days')""");
		avanzar(Duration.ofDays(30));

		assertThat(caducidad.ejecutar()).isZero();
		assertThat(pago("PAG-0099").get("revision")).isEqualTo("POR_REVISAR");
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(auditoriasAutomaticas()).isZero();
	}

	@Test
	void unPagoDevueltoAPorRevisarHoyNoSeRechazaAunqueLaReservaSeaDeHaceDias() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		revisar("PAG-0001", "Rechazado", "No aparece").andExpect(status().isOk());
		avanzar(Duration.ofDays(3));
		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk());

		assertThat(caducidad.ejecutar()).isZero();
		avanzar(DOCE_HORAS.minusMinutes(1));
		assertThat(caducidad.ejecutar()).isZero();
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
		avanzar(Duration.ofMinutes(1));
		assertThat(caducidad.ejecutar()).isEqualTo(1);
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("RECHAZADO");
	}

	@Test
	void laAuditoriaEsRechazoAutomaticoSinMotivoNiDatosPersonalesEnLogsNiEnDetalle(CapturedOutput salida)
			throws Exception {
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);
		caducidad.ejecutar();

		Map<String, Object> fila = jdbc.queryForMap("select * from auditoria where accion = 'RECHAZO_AUTOMATICO'");
		assertThat(fila.get("administrador_id")).isNull();
		assertThat(fila.get("entidad")).isEqualTo("PAGO");
		assertThat(fila.get("entidad_ref")).isEqualTo("PAG-0001");
		assertThat(fila.get("detalle")).isEqualTo("POR_REVISAR>RECHAZADO");
		assertThat(salida.getAll()).doesNotContain("1065123456", "1065999888", "ana@example.com", "Ana María",
				"Penicilina", MOTIVO);
	}

	@Test
	void laEncargadaPuedeReabrirElPagoRechazadoAutomaticamenteYLaReservaVuelveAActiva() throws Exception {
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);
		caducidad.ejecutar();
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);

		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk());
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
		assertThat(pago("PAG-0001").get("motivo_rechazo")).isNull();
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(documentosActivos("PER-0001")).isEqualTo(2);
		// Recién reabierto: no se vuelve a rechazar.
		assertThat(caducidad.ejecutar()).isZero();
	}

	@Test
	void siOtraInscripcionTomoElDocumentoReabrirDa409() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);
		caducidad.ejecutar();
		enviar(ReservaJson.titular()).andExpect(status().isCreated());

		revisar("PAG-0001", "Por revisar", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"));
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("RECHAZADO");
	}

	@Test
	void esIdempotenteDosEjecucionesSeguidasNoDuplicanNada() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);

		assertThat(caducidad.ejecutar()).isEqualTo(1);
		assertThat(caducidad.ejecutar()).isZero();
		assertThat(auditoriasAutomaticas()).isEqualTo(1);
		assertThat(pago("PAG-0001").get("motivo_rechazo")).isEqualTo(MOTIVO);
	}

	@Test
	void procesaVariasReservasPorLotesYNoTocaLasQueTodavíaNoVencen() throws Exception {
		enviar(ReservaJson.titular().set("document", "1065000001")).andExpect(status().isCreated());
		enviar(ReservaJson.titular().set("document", "1065000002")).andExpect(status().isCreated());
		enviar(ReservaJson.titular().set("document", "1065000003")).andExpect(status().isCreated());
		avanzar(DOCE_HORAS);
		// La cuarta nace ahora: no vence.
		enviar(ReservaJson.titular().set("document", "1065000004")).andExpect(status().isCreated());

		assertThat(caducidad.ejecutar()).isEqualTo(3);
		assertThat(jdbc.queryForObject("select count(*) from inscripcion where activa", Integer.class)).isEqualTo(1);
		assertThat(auditoriasAutomaticas()).isEqualTo(3);
	}

	@Test
	void elPagoManualNoSeToca() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		pagoManual("PER-0001", "{\"amount\":350000}").andExpect(status().isCreated());
		avanzar(Duration.ofDays(2));
		assertThat(caducidad.ejecutar()).isZero();
		assertThat(pago("PAG-0002").get("revision")).isEqualTo("APROBADO");
	}

	@RepeatedTest(5)
	void tareaYAprobacionSimultaneasDejanElEstadoCoherente() throws Exception {
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		adminId();
		avanzar(DOCE_HORAS);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch salida = new CountDownLatch(1);
			List<Future<Integer>> futuros = new ArrayList<>();
			futuros.add(pool.submit(() -> {
				salida.await();
				return caducidad.ejecutar();
			}));
			futuros.add(pool.submit(() -> {
				salida.await();
				return revisar("PAG-0001", "Aprobado", null).andReturn().getResponse().getStatus();
			}));
			salida.countDown();
			for (Future<Integer> f : futuros) {
				f.get(60, TimeUnit.SECONDS);
			}
		}
		finally {
			pool.shutdownNow();
		}

		String revision = (String) pago("PAG-0001").get("revision");
		boolean activa = (Boolean) inscripcion("PER-0001").get("activa");
		// Nunca un pago aprobado en una reserva inactiva, ni una rechazada con documentos retenidos.
		assertThat(activa).isEqualTo(!"RECHAZADO".equals(revision));
		assertThat(documentosActivos("PER-0001")).isEqualTo(activa ? 2 : 0);
		assertThat(revision).isIn("APROBADO", "RECHAZADO");
		assertThat(auditoriasAutomaticas()).isLessThanOrEqualTo(1);
	}

	@Test
	void laTareaEstaProgramadaConCronYSoloSeActivaPorPropiedad() throws Exception {
		Method metodo = CaducidadReservasTask.class.getMethod("ejecutar");
		Scheduled programada = metodo.getAnnotation(Scheduled.class);
		assertThat(programada).isNotNull();
		assertThat(programada.cron()).contains("app.reservas.caducidad.cron");
		ConditionalOnProperty condicion = CaducidadReservasTask.class.getAnnotation(ConditionalOnProperty.class);
		assertThat(condicion.name()).containsExactly("app.reservas.caducidad.activa");
	}

}
