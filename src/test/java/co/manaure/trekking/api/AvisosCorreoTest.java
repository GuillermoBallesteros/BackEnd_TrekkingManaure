package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.manaure.trekking.notificacion.ClienteBrevo;
import co.manaure.trekking.notificacion.Destinatario;
import co.manaure.trekking.notificacion.ErrorBrevo;
import co.manaure.trekking.notificacion.RecordatorioPagoService;
import co.manaure.trekking.notificacion.TipoAviso;
import co.manaure.trekking.pago.CaducidadReservasService;
import co.manaure.trekking.pago.PagoRevisadoEvent;
import co.manaure.trekking.pago.RevisionPago;

/**
 * HU-N15 con PostgreSQL real, reloj controlable y Brevo simulado: recordatorios del saldo, aprobación y rechazo tras
 * el commit, víspera del evento, idempotencia, reintentos y privacidad de los logs.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = { "app.brevo.activo=true", "app.brevo.api-key=clave-prueba",
		"app.brevo.plantillas.pago-3-dias=1", "app.brevo.plantillas.pago-1-dia=2", "app.brevo.plantillas.pago-hoy=3",
		"app.brevo.plantillas.pago-vencido=4", "app.brevo.plantillas.pago-aprobado=5",
		"app.brevo.plantillas.pago-rechazado=6", "app.brevo.plantillas.evento-manana=7", "app.brevo.lote=1" })
class AvisosCorreoTest extends ReservaPublicaBase {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	@MockitoBean
	ClienteBrevo cliente;
	@Autowired
	RecordatorioPagoService recordatorios;
	@Autowired
	CaducidadReservasService caducidad;
	@Autowired
	ApplicationEventPublisher eventos;
	@Autowired
	PlatformTransactionManager transacciones;

	private record Envio(Destinatario para, long plantilla, Map<String, Object> params, TipoAviso tipo) {
	}

	@BeforeEach
	void responderComoBrevo() {
		when(cliente.enviar(any(), anyLong(), anyMap(), any())).thenReturn("<id@smtp-relay.mailin.fr>");
	}

	/** Mediodía en Bogotá del día indicado de 2026. */
	private void dia(int mes, int dia) {
		reloj.fijar(ZonedDateTime.of(2026, mes, dia, 12, 0, 0, 0, BOGOTA).toInstant());
	}

	@SuppressWarnings("unchecked")
	private List<Envio> envios() {
		return Mockito.mockingDetails(cliente).getInvocations().stream()
				.filter(i -> i.getMethod().getName().equals("enviar"))
				.map(i -> new Envio((Destinatario) i.getArgument(0), (Long) i.getArgument(1),
						(Map<String, Object>) i.getArgument(2), (TipoAviso) i.getArgument(3)))
				.toList();
	}

	private List<TipoAviso> tipos() {
		return envios().stream().map(Envio::tipo).toList();
	}

	/** Espera a que el hilo de avisos envíe {@code tipo}: los avisos de una revisión salen fuera de la petición. */
	private void esperarEnvio(TipoAviso tipo) {
		verify(cliente, timeout(5000).atLeastOnce()).enviar(any(), anyLong(), anyMap(), eq(tipo));
	}

	/** Da tiempo al hilo de avisos y comprueba que no hubo más envíos que {@code total}. */
	private void sinMasEnviosQue(int total) {
		verify(cliente, after(500).times(total)).enviar(any(), anyLong(), anyMap(), any());
	}

	private Map<String, Object> fila(TipoAviso tipo) {
		return jdbc.queryForMap("select * from envio_aviso where tipo = ?", tipo.name());
	}

	/** Titular solo, plan apartado con compromiso el 9 de noviembre y la separación de 50.000 ya aprobada. */
	private void reservaConSeparacionAprobada() throws Exception {
		enviar(ReservaJson.titular().set("plan", "apartado").set("commitmentDate", "2026-11-09"))
				.andExpect(status().isCreated());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		esperarEnvio(TipoAviso.PAGO_APROBADO);
		clearInvocations(cliente);
	}

	@Test
	void laTareaAvisaElSaldo3Dias1DiaElMismoDiaYAlVencerSinRepetir(CapturedOutput salida) throws Exception {
		reservaConSeparacionAprobada();

		dia(11, 5);
		assertThat(recordatorios.ejecutar()).isZero();
		dia(11, 6);
		assertThat(recordatorios.ejecutar()).isEqualTo(1);
		assertThat(recordatorios.ejecutar()).isZero();

		Envio tresDias = envios().get(0);
		assertThat(tresDias.tipo()).isEqualTo(TipoAviso.PAGO_3_DIAS);
		assertThat(tresDias.plantilla()).isEqualTo(1);
		assertThat(tresDias.para()).isEqualTo(new Destinatario("Ana Maria Perez Gomez", "ana@example.com"));
		assertThat(tresDias.params()).containsEntry("nombre", "Ana Maria").containsEntry("codigoReserva", "PER-0001")
				.containsEntry("total", "$350.000").containsEntry("pagado", "$50.000")
				.containsEntry("saldo", "$300.000").containsEntry("pagoCompleto", false)
				.containsEntry("fechaCompromiso", "9 de noviembre").containsEntry("fechaEvento", "28 de noviembre")
				.doesNotContainKeys("documento", "eps", "salud", "codigoPago");
		assertThat(tresDias.params().values()).doesNotContain("1065123456");

		dia(11, 8);
		recordatorios.ejecutar();
		dia(11, 9);
		recordatorios.ejecutar();
		dia(11, 10);
		recordatorios.ejecutar();
		dia(11, 11);
		recordatorios.ejecutar();

		assertThat(tipos()).containsExactly(TipoAviso.PAGO_3_DIAS, TipoAviso.PAGO_1_DIA, TipoAviso.PAGO_HOY,
				TipoAviso.PAGO_VENCIDO);
		assertThat(jdbc.queryForObject("select count(*) from envio_aviso where estado = 'ENVIADO'", Integer.class))
				.isEqualTo(5);
		assertThat(fila(TipoAviso.PAGO_HOY)).containsEntry("referencia", "2026-11-09")
				.containsEntry("mensaje_id", "<id@smtp-relay.mailin.fr>").containsEntry("intentos", 1);
		assertThat(salida.getAll()).doesNotContain("ana@example.com", "ANA MARIA", "Ana Maria", "1065123456");
	}

	@Test
	void conLaSeparacionPorRevisarNoHayRecordatoriosDelSaldo() throws Exception {
		enviar(ReservaJson.titular().set("plan", "apartado").set("commitmentDate", "2026-11-09"))
				.andExpect(status().isCreated());
		dia(11, 6);
		assertThat(recordatorios.ejecutar()).isZero();
		assertThat(envios()).isEmpty();
	}

	@Test
	void elPagoManualAvisaLaAprobacionYConElSaldoPagadoNoHayRecordatorios() throws Exception {
		reservaConSeparacionAprobada();
		pagoManual("PER-0001", "{\"amount\":300000}").andExpect(status().isCreated());
		esperarEnvio(TipoAviso.PAGO_APROBADO);

		assertThat(envios()).singleElement().satisfies(e -> {
			assertThat(e.tipo()).isEqualTo(TipoAviso.PAGO_APROBADO);
			assertThat(e.params()).containsEntry("codigoPago", "PAG-0002").containsEntry("concepto", "Pago de saldo")
					.containsEntry("monto", "$300.000").containsEntry("saldo", "$0")
					.containsEntry("pagoCompleto", true);
		});
		clearInvocations(cliente);
		for (int d : new int[] { 6, 8, 9, 10 }) {
			dia(11, d);
			assertThat(recordatorios.ejecutar()).isZero();
		}
		assertThat(envios()).isEmpty();
	}

	@Test
	void aprobarYRechazarAvisanAlInstanteYUnaSolaVezPorPago() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());

		revisar("PAG-0001", "Rechazado", "El pago no aparece en la llave Bre-B").andExpect(status().isOk());
		esperarEnvio(TipoAviso.PAGO_RECHAZADO);
		assertThat(envios()).singleElement().satisfies(e -> {
			assertThat(e.tipo()).isEqualTo(TipoAviso.PAGO_RECHAZADO);
			assertThat(e.plantilla()).isEqualTo(6);
			assertThat(e.params()).containsEntry("motivo", "El pago no aparece en la llave Bre-B")
					.containsEntry("concepto", "Pago total").containsEntry("monto", "$350.000");
		});

		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk());
		revisar("PAG-0001", "Rechazado", "Otro motivo").andExpect(status().isOk());
		sinMasEnviosQue(1);
		assertThat(tipos()).containsExactly(TipoAviso.PAGO_RECHAZADO);

		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		esperarEnvio(TipoAviso.PAGO_APROBADO);
		sinMasEnviosQue(2);
		assertThat(tipos()).containsExactly(TipoAviso.PAGO_RECHAZADO, TipoAviso.PAGO_APROBADO);
		assertThat(envios().get(1).params()).containsEntry("pagoCompleto", true).containsEntry("motivo", "");
	}

	@Test
	void laCaducidadAvisaElRechazoConSuMotivo() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		reloj.fijar(reloj.instant().plus(Duration.ofHours(12)));

		assertThat(caducidad.ejecutar()).isEqualTo(1);
		esperarEnvio(TipoAviso.PAGO_RECHAZADO);
		assertThat(envios()).singleElement().satisfies(e -> {
			assertThat(e.tipo()).isEqualTo(TipoAviso.PAGO_RECHAZADO);
			assertThat(e.params()).containsEntry("motivo", "No se verificó el pago dentro de 12 horas.");
		});
	}

	@Test
	void unaRevisionQueFallaOSeRevierteNoEnviaCorreo() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		revisar("PAG-0001", "Rechazado", "  ").andExpect(status().isUnprocessableEntity());

		new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
			jdbc.update("update pago set revision = 'RECHAZADO', motivo_rechazo = 'x', revisado_en = now() where codigo = 'PAG-0001'");
			eventos.publishEvent(new PagoRevisadoEvent("PAG-0001", RevisionPago.RECHAZADO));
			estado.setRollbackOnly();
		});

		sinMasEnviosQue(0);
		assertThat(jdbc.queryForObject("select count(*) from envio_aviso", Integer.class)).isZero();
	}

	@Test
	void laVisperaDelEventoAvisaACadaPersonaDeUnaReservaPagada() throws Exception {
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		aprobarPagos("PER-0001");
		esperarEnvio(TipoAviso.PAGO_APROBADO);
		clearInvocations(cliente);

		dia(11, 26);
		assertThat(recordatorios.ejecutar()).isZero();
		dia(11, 27);
		assertThat(recordatorios.ejecutar()).isEqualTo(2);
		assertThat(recordatorios.ejecutar()).isZero();

		assertThat(envios()).extracting(Envio::tipo).containsOnly(TipoAviso.EVENTO_MANANA);
		assertThat(envios()).extracting(e -> e.para().correo()).containsExactly("ana@example.com",
				"carlos@example.com");
		assertThat(envios()).extracting(e -> e.params().get("nombre")).containsExactly("Ana Maria", "Carlos");
		assertThat(envios().get(0).plantilla()).isEqualTo(7);
	}

	@Test
	void unFalloDeBrevoQuedaFallidoYSeReintentaSiLaTareaCorreOtraVezEseDia(CapturedOutput salida) throws Exception {
		reservaConSeparacionAprobada();
		dia(11, 6);
		when(cliente.enviar(any(), anyLong(), anyMap(), any())).thenThrow(new ErrorBrevo(503, null));

		assertThat(recordatorios.ejecutar()).isZero();
		assertThat(fila(TipoAviso.PAGO_3_DIAS)).containsEntry("estado", "FALLIDO").containsEntry("intentos", 1);
		assertThat(salida.getAll()).contains("Avisos: no se pudo enviar PAGO_3_DIAS de la reserva PER-0001")
				.doesNotContain("ana@example.com");

		Mockito.reset(cliente);
		when(cliente.enviar(any(), anyLong(), anyMap(), any())).thenReturn("<otro@brevo>");
		assertThat(recordatorios.ejecutar()).isEqualTo(1);
		assertThat(fila(TipoAviso.PAGO_3_DIAS)).containsEntry("estado", "ENVIADO").containsEntry("intentos", 2)
				.containsEntry("mensaje_id", "<otro@brevo>");
	}

}
