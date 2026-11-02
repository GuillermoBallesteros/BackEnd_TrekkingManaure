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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.manaure.trekking.notificacion.ClienteBrevo;
import co.manaure.trekking.notificacion.Destinatario;
import co.manaure.trekking.notificacion.RecordatorioPagoService;
import co.manaure.trekking.notificacion.TipoAviso;

/**
 * RN-24 con Brevo simulado: acuse de la reserva recibida, plazo ampliado (RN-25) y reserva vencida rechazada (RN-26),
 * cada uno una sola vez, tras el commit y sin datos sensibles.
 */
@TestPropertySource(properties = { "app.brevo.activo=true", "app.brevo.api-key=clave-prueba",
		"app.brevo.plantillas.pago-3-dias=1", "app.brevo.plantillas.pago-1-dia=2", "app.brevo.plantillas.pago-hoy=3",
		"app.brevo.plantillas.pago-vencido=4", "app.brevo.plantillas.pago-aprobado=5",
		"app.brevo.plantillas.pago-rechazado=6", "app.brevo.plantillas.evento-manana=7",
		"app.brevo.plantillas.reserva-recibida=8", "app.brevo.plantillas.plazo-ampliado=9",
		"app.brevo.plantillas.reserva-rechazada=10", "app.brevo.lote=1" })
class AvisosReservaTest extends ReservaPublicaBase {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	@MockitoBean
	ClienteBrevo cliente;
	@Autowired
	RecordatorioPagoService recordatorios;

	private record Envio(Destinatario para, long plantilla, Map<String, Object> params, TipoAviso tipo) {
	}

	@BeforeEach
	void responderComoBrevo() {
		when(cliente.enviar(any(), anyLong(), anyMap(), any())).thenReturn("<id@smtp-relay.mailin.fr>");
	}

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

	private void esperarEnvio(TipoAviso tipo) {
		verify(cliente, timeout(5000).atLeastOnce()).enviar(any(), anyLong(), anyMap(), eq(tipo));
	}

	private void sinMasEnviosQue(int total) {
		verify(cliente, after(500).times(total)).enviar(any(), anyLong(), anyMap(), any());
	}

	private Map<String, Object> fila(TipoAviso tipo) {
		return jdbc.queryForMap("select * from envio_aviso where tipo = ?", tipo.name());
	}

	/** Apartado con compromiso el 9 de noviembre, separación aprobada y hoy 10 de noviembre (vencida). */
	private void reservaVencida() throws Exception {
		enviar(ReservaJson.titular().set("plan", "apartado").set("commitmentDate", "2026-11-09"))
				.andExpect(status().isCreated());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		esperarEnvio(TipoAviso.PAGO_APROBADO);
		dia(11, 10);
		clearInvocations(cliente);
	}

	@Test
	void crearLaReservaEnviaElAcuseConElPagoPorVerificarUnaSolaVez() throws Exception {
		enviar(ReservaJson.titular().set("plan", "apartado").set("commitmentDate", "2026-11-09"))
				.andExpect(status().isCreated());
		esperarEnvio(TipoAviso.RESERVA_RECIBIDA);
		sinMasEnviosQue(1);

		assertThat(envios()).singleElement().satisfies(e -> {
			assertThat(e.plantilla()).isEqualTo(8);
			assertThat(e.para()).isEqualTo(new Destinatario("Ana Maria Perez Gomez", "ana@example.com"));
			assertThat(e.params()).containsEntry("codigoReserva", "PER-0001").containsEntry("concepto", "Separación de cupo")
					.containsEntry("monto", "$50.000").containsEntry("total", "$350.000")
					.containsEntry("fechaCompromiso", "9 de noviembre").doesNotContainKeys("documento", "eps", "salud");
		});
		assertThat(fila(TipoAviso.RESERVA_RECIBIDA)).containsEntry("referencia", "PER-0001")
				.containsEntry("estado", "ENVIADO");
	}

	@Test
	void unaReservaQueFallaPorValidacionNoEnviaAcuse() throws Exception {
		enviar(ReservaJson.titular().set("document", "12")).andExpect(status().is4xxClientError());
		sinMasEnviosQue(0);
	}

	@Test
	void ampliarElPlazoAvisaLaNuevaFechaYLosRecordatoriosSiguenLaNuevaFecha() throws Exception {
		reservaVencida();

		mvc.perform(put("/api/v1/admin/reservas/PER-0001/plazo").header("Authorization", "Bearer " + tokenAdmin())
				.contentType(MediaType.APPLICATION_JSON).content("{\"days\":3}")).andExpect(status().isOk());
		esperarEnvio(TipoAviso.PLAZO_AMPLIADO);
		assertThat(envios()).singleElement().satisfies(e -> {
			assertThat(e.plantilla()).isEqualTo(9);
			assertThat(e.params()).containsEntry("fechaCompromiso", "13 de noviembre").containsEntry("saldo", "$300.000")
					.containsEntry("pagado", "$50.000");
		});
		assertThat(fila(TipoAviso.PLAZO_AMPLIADO)).containsEntry("referencia", "2026-11-13");

		// Hoy faltan 3 días para la nueva fecha: el recordatorio usa la fecha ampliada.
		clearInvocations(cliente);
		assertThat(recordatorios.ejecutar()).isEqualTo(1);
		assertThat(envios()).singleElement().satisfies(e -> assertThat(e.tipo()).isEqualTo(TipoAviso.PAGO_3_DIAS));
		assertThat(jdbc.queryForObject("select referencia from envio_aviso where tipo = 'PAGO_3_DIAS'", String.class))
				.isEqualTo("2026-11-13");
	}

	@Test
	void rechazarLaReservaVencidaAvisaUnaVezYNoMandaRechazoDePago() throws Exception {
		reservaVencida();

		mvc.perform(post("/api/v1/admin/reservas/PER-0001/rechazo").header("Authorization", "Bearer " + tokenAdmin()))
				.andExpect(status().isOk());
		esperarEnvio(TipoAviso.RESERVA_RECHAZADA);
		sinMasEnviosQue(1);
		assertThat(envios()).singleElement().satisfies(e -> {
			assertThat(e.plantilla()).isEqualTo(10);
			assertThat(e.params()).containsEntry("codigoReserva", "PER-0001").containsEntry("saldo", "$300.000");
		});

		// Rechazada e inactiva: la tarea diaria ya no le manda recordatorios.
		clearInvocations(cliente);
		dia(11, 26);
		recordatorios.ejecutar();
		dia(11, 27);
		recordatorios.ejecutar();
		assertThat(envios()).isEmpty();
	}

}
