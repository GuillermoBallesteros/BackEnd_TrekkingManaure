package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** HU-N09 con PostgreSQL 18 real: pago manual de la administradora. */
class PagoManualTest extends ReservaPublicaBase {

	private void crear(ReservaJson r) throws Exception {
		enviar(r).andExpect(status().isCreated());
	}

	private static String monto(long valor) {
		return "{\"amount\":" + valor + "}";
	}

	/** Total 650000 (titular + acompañante sin kit + cuatrimoto) con pago inicial de 50000. */
	private ReservaJson apartado() {
		return ReservaJson.conAcompanante().set("extras", List.of("cuatrimoto")).set("plan", "apartado")
				.set("commitmentDate", "2026-11-09");
	}

	private ReservaJson menor() {
		return ReservaJson.titular().set("documentType", "TI").set("document", "1234567890")
				.set("birthDate", "2012-03-01").set("emergencyContact.relationship", "Madre");
	}

	private Map<String, Object> pago(String codigo) {
		return jdbc.queryForMap("select * from pago where codigo = ?", codigo);
	}

	private Map<String, Object> inscripcion(String codigo) {
		return jdbc.queryForMap("select * from inscripcion where codigo = ?", codigo);
	}

	/** Cupos del páramo ocupados según {@code GET /api/v1/disponibilidad}. */
	private int cuposParamo() throws Exception {
		String json = mvc.perform(get("/api/v1/disponibilidad")).andReturn().getResponse().getContentAsString();
		return new tools.jackson.databind.json.JsonMapper().readTree(json).get("paramo").get("occupied").asInt();
	}

	// --- éxito

	@Test
	void pagoDe350000EnReservaDe350000EsPagoTotalAprobadoYDeHoy() throws Exception {
		crear(ReservaJson.titular());
		pagoManual("PER-0001", monto(350000)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("PAG-0002")).andExpect(jsonPath("$.concept").value("Pago total"))
				.andExpect(jsonPath("$.amount").value(350000)).andExpect(jsonPath("$.date").value("2026-10-28"))
				.andExpect(jsonPath("$.review").value("Aprobado"))
				.andExpect(jsonPath("$.rejectionReason").value(nullValue()));
	}

	@Test
	void pagoDe50000EnReservaDe650000EsSeparacionDeCupo() throws Exception {
		crear(apartado());
		pagoManual("PER-0001", monto(50000)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.concept").value("Separación de cupo"));
	}

	@Test
	void con50000AprobadosElPagoDe600000EsDeSaldoYLaReservaQuedaPagada() throws Exception {
		crear(apartado());
		aprobarPagos("PER-0001");
		pagoManual("PER-0001", monto(600000)).andExpect(status().isCreated())
				.andExpect(jsonPath("$.concept").value("Pago de saldo"));
		assertThat(jdbc.queryForObject("""
				select sum(monto) from pago where revision = 'APROBADO'""", Long.class)).isEqualTo(650000L);
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
	}

	@Test
	void seGuardanOrigenManualRegistradoPorRevisadoPorYSeAuditaConCodigoYMonto() throws Exception {
		crear(ReservaJson.titular());
		pagoManual("PER-0001", monto(350000)).andExpect(status().isCreated());
		Map<String, Object> p = pago("PAG-0002");
		assertThat(p.get("origen")).isEqualTo("MANUAL");
		assertThat(p.get("registrado_por")).isEqualTo(adminId());
		assertThat(p.get("revisado_por")).isEqualTo(adminId());
		assertThat(p.get("revisado_en")).isNotNull();
		assertThat(p.get("revision")).isEqualTo("APROBADO");
		assertThat(p.get("motivo_rechazo")).isNull();

		Map<String, Object> a = jdbc.queryForMap("""
				select accion, entidad, entidad_ref, detalle, administrador_id from auditoria
				where accion = 'REGISTRAR_PAGO_MANUAL'""");
		assertThat(a.get("entidad_ref")).isEqualTo("PAG-0002");
		assertThat(a.get("detalle").toString()).contains("350000").doesNotContain("Ana").doesNotContain("1065123456");
		assertThat(a.get("administrador_id")).isEqualTo(adminId());
	}

	@Test
	void laCamaSeApartaAlReservarYElPagoManualNoLaCambia() throws Exception {
		crear(ReservaJson.titular());
		assertThat(cuposParamo()).isEqualTo(1);
		pagoManual("PER-0001", monto(350000)).andExpect(status().isCreated());
		assertThat(cuposParamo()).isEqualTo(1);
	}

	@Test
	void elPagoManualReactivaUnaReservaRechazada() throws Exception {
		crear(ReservaJson.conAcompanante());
		revisar("PAG-0001", "Rechazado", "No aparece").andExpect(status().isOk());
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);

		pagoManual("PER-0001", monto(50000)).andExpect(status().isCreated());
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(jdbc.queryForObject("""
				select count(*) from inscripcion_participante where documento_activo is not null""", Integer.class))
				.isEqualTo(2);
	}

	// --- errores

	@Test
	void sinTokenDa401() throws Exception {
		crear(ReservaJson.titular());
		mvc.perform(post("/api/v1/admin/reservas/PER-0001/pagos").contentType(MediaType.APPLICATION_JSON)
				.content(monto(1000))).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void reservaInexistenteDa404() throws Exception {
		pagoManual("PER-9999", monto(1000)).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVA_NO_ENCONTRADA"))
				.andExpect(jsonPath("$.detail").value("La inscripción ya no existe."));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"amount\":null}", "{\"amount\":1000.5}", "{\"amount\":1000.0}",
			"{\"amount\":0}", "{\"amount\":-5}", "{\"amount\":\"1000\"}", "{\"amount\":true}" })
	void montoAusenteDecimalCeroONegativoDa422(String cuerpo) throws Exception {
		crear(ReservaJson.titular());
		pagoManual("PER-0001", cuerpo).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("MONTO_INVALIDO"))
				.andExpect(jsonPath("$.detail").value("Escribe un monto válido."));
		assertThat(contar("pago")).isEqualTo(1);
	}

	@Test
	void montoMayorQueElSaldoDa422() throws Exception {
		crear(ReservaJson.titular());
		pagoManual("PER-0001", monto(350001)).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("MONTO_SUPERA_SALDO"))
				.andExpect(jsonPath("$.detail").value("El monto supera el saldo pendiente."));
		assertThat(contar("pago")).isEqualTo(1);
	}

	@Test
	void conLaReservaPagadaCualquierMontoSuperaElSaldo() throws Exception {
		crear(ReservaJson.titular());
		aprobarPagos("PER-0001");
		pagoManual("PER-0001", monto(1)).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("MONTO_SUPERA_SALDO"));
	}

	@Test
	void titularMenorSinAutorizacionDa422YLuegoFunciona() throws Exception {
		crear(menor());
		pagoManual("PER-0001", monto(350000)).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("AUTORIZACION_MENOR_PENDIENTE"));
		assertThat(contar("pago")).isEqualTo(1);

		jdbc.update("update inscripcion set autorizacion_menor = true");
		pagoManual("PER-0001", monto(350000)).andExpect(status().isCreated());
	}

	@Test
	void reactivarConPagoManualSinCamaEnElParamoDa409() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 1");
		crear(ReservaJson.titular());
		// Rechazada, libera su cupo y otra reserva la toma.
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		crear(ReservaJson.titular().set("document", "1065000111"));

		pagoManual("PER-0001", monto(350000)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SIN_CUPO_PARA_APROBAR"));
		assertThat(contar("pago")).isEqualTo(2);
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);
	}

	@Test
	void unSegundoPagoManualDeUnaReservaQueYaOcupaCupoNoRevalidaCupos() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 2");
		crear(apartado());
		aprobarPagos("PER-0001");
		jdbc.update("update configuracion_evento set cupos_paramo = 1");
		pagoManual("PER-0001", monto(600000)).andExpect(status().isCreated());
	}

	@Test
	void reservaRechazadaConDocumentoTomadoPorOtraDa409() throws Exception {
		crear(ReservaJson.titular());
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		enviar(ReservaJson.titular()).andExpect(status().isCreated());

		pagoManual("PER-0001", monto(350000)).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"));
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);
		assertThat(contar("pago")).isEqualTo(2);
	}

	// --- concurrencia

	@RepeatedTest(5)
	void dosPagosManualesSimultaneosQueSuperanElSaldoDan201Y422() throws Exception {
		crear(apartado());
		aprobarPagos("PER-0001"); // saldo 600000
		String token = tokenAdmin();
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				futuros.add(pool.submit(() -> {
					salida.await();
					return mvc.perform(post("/api/v1/admin/reservas/PER-0001/pagos")
							.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
							.content(monto(400000))).andReturn().getResponse().getStatus();
				}));
			}
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> f : futuros) {
				estados.add(f.get(60, TimeUnit.SECONDS));
			}
			assertThat(estados).containsExactlyInAnyOrder(201, 422);
		} finally {
			pool.shutdownNow();
		}
		assertThat(jdbc.queryForObject("select sum(monto) from pago where revision = 'APROBADO'", Long.class))
				.isEqualTo(450000L);
	}

}
