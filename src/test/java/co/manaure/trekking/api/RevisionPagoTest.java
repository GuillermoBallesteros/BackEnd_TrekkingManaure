package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** HU-N08 con PostgreSQL 18 real: revisión de pagos (aprobar, rechazar, devolver a "Por revisar"). */
class RevisionPagoTest extends ReservaPublicaBase {

	private static final String RUTA = "/api/v1/admin/pagos/PAG-0001/revision";

	private void crear(ReservaJson r) throws Exception {
		enviar(r).andExpect(status().isCreated());
	}

	/** Titular con 350000 de total y un pago inicial de 350000 (PER-0001 / PAG-0001). */
	private void crearTitular() throws Exception {
		crear(ReservaJson.titular());
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

	private ReservaJson otra(String doc, String docAcompanante) {
		return ReservaJson.conAcompanante().set("document", doc).set("companion.document", docAcompanante);
	}

	private Map<String, Object> pago(String codigo) {
		return jdbc.queryForMap("select * from pago where codigo = ?", codigo);
	}

	private Map<String, Object> inscripcion(String codigo) {
		return jdbc.queryForMap("select * from inscripcion where codigo = ?", codigo);
	}

	private List<String> documentosActivos(String codigoInscripcion) {
		return jdbc.queryForList("""
				select documento_activo from inscripcion_participante
				where inscripcion_id = (select id from inscripcion where codigo = ?) and documento_activo is not null""",
				String.class, codigoInscripcion);
	}

	private int ocupados() throws Exception {
		String json = mvc.perform(get("/api/v1/disponibilidad")).andReturn().getResponse().getContentAsString();
		return new tools.jackson.databind.json.JsonMapper().readTree(json).get("occupied").asInt();
	}

	// --- éxito

	@Test
	void sinTokenDa401() throws Exception {
		crearTitular();
		mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"review\":\"Aprobado\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void aprobarUnPagoPorRevisarDevuelveLaReservaCompleta() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("PER-0001"))
				.andExpect(jsonPath("$.firstName").value("ANA MARIA"))
				.andExpect(jsonPath("$.payments[0].id").value("PAG-0001"))
				.andExpect(jsonPath("$.payments[0].review").value("Aprobado"))
				.andExpect(jsonPath("$.payments[0].rejectionReason").value(nullValue()));
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("APROBADO");
	}

	@Test
	void pagoDe350000EnReservaDe350000QuedaPagadoYActiva() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(350000))
				.andExpect(jsonPath("$.payments[0].amount").value(350000));
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		// Aprobado cubre el total: estado derivado Pagado.
		assertThat(jdbc.queryForObject("select sum(monto) from pago where revision = 'APROBADO'", Long.class))
				.isEqualTo(jdbc.queryForObject("select total from inscripcion", Long.class));
	}

	@Test
	void pagoDe50000EnReservaDe650000QuedaConSaldoPendiente() throws Exception {
		crear(apartado());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(650000))
				.andExpect(jsonPath("$.payments[0].amount").value(50000));
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(jdbc.queryForObject("select sum(monto) from pago where revision = 'APROBADO'", Long.class))
				.isEqualTo(50000L);
	}

	@Test
	void aprobarElPrimerPagoConTitularEnParamoCambiaLaDisponibilidadDe0a1() throws Exception {
		crearTitular();
		assertThat(ocupados()).isZero();
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		assertThat(ocupados()).isEqualTo(1);
	}

	@Test
	void rechazarConMotivoLoGuardaSinEspaciosSobrantes() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Rechazado", "  El pago no aparece en la llave Bre-B \n").andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].review").value("Rechazado"))
				.andExpect(jsonPath("$.payments[0].rejectionReason").value("El pago no aparece en la llave Bre-B"));
		assertThat(pago("PAG-0001").get("motivo_rechazo")).isEqualTo("El pago no aparece en la llave Bre-B");
	}

	@Test
	void rechazarElUnicoPagoDejaLaReservaInactivaYLiberaElDocumento() throws Exception {
		crear(ReservaJson.conAcompanante());
		revisar("PAG-0001", "Rechazado", "No aparece").andExpect(status().isOk());
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);
		assertThat(documentosActivos("PER-0001")).isEmpty();
		// El mismo documento (y el del acompañante) queda libre para una nueva reserva.
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("PER-0002"));
	}

	@Test
	void devolverUnPagoAprobadoAPorRevisarBorraRevisorYLiberaLosCupos() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		assertThat(ocupados()).isEqualTo(1);
		assertThat(pago("PAG-0001").get("revisado_por")).isEqualTo(adminId());
		assertThat(pago("PAG-0001").get("revisado_en")).isNotNull();

		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].review").value("Por revisar"))
				.andExpect(jsonPath("$.payments[0].rejectionReason").value(nullValue()));
		assertThat(ocupados()).isZero();
		assertThat(pago("PAG-0001").get("revisado_por")).isNull();
		assertThat(pago("PAG-0001").get("revisado_en")).isNull();
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
	}

	@Test
	void devolverUnPagoRechazadoAPorRevisarBorraElMotivoYReactivaLaReserva() throws Exception {
		crear(ReservaJson.conAcompanante());
		revisar("PAG-0001", "Rechazado", "No aparece").andExpect(status().isOk());

		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].rejectionReason").value(nullValue()));
		assertThat(pago("PAG-0001").get("motivo_rechazo")).isNull();
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(documentosActivos("PER-0001")).hasSize(2);
	}

	@Test
	void aprobarUnPagoReactivaUnaReservaRechazada() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].rejectionReason").value(nullValue()));
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(documentosActivos("PER-0001")).hasSize(1);
		assertThat(pago("PAG-0001").get("revisado_por")).isEqualTo(adminId());
	}

	@Test
	void aprobarUnPagoYaAprobadoEsIdempotente() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		Object revisadoEn = pago("PAG-0001").get("revisado_en");

		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].review").value("Aprobado"));
		assertThat(pago("PAG-0001").get("revisado_en")).isEqualTo(revisadoEn);
		assertThat(jdbc.queryForObject("select count(*) from auditoria where entidad = 'PAGO'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void cadaTransicionSeAuditaConCodigoYTransicionSinElTextoDelMotivo() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk());
		revisar("PAG-0001", "Rechazado", "Motivo secreto del rechazo").andExpect(status().isOk());

		List<Map<String, Object>> filas = jdbc.queryForList("""
				select accion, entidad, entidad_ref, detalle, administrador_id from auditoria
				where entidad = 'PAGO' order by id""");
		assertThat(filas).extracting(f -> f.get("accion")).containsExactly("APROBAR_PAGO",
				"DEVOLVER_PAGO_A_REVISION", "RECHAZAR_PAGO");
		assertThat(filas).extracting(f -> f.get("entidad_ref")).containsOnly("PAG-0001");
		assertThat(filas).extracting(f -> f.get("detalle")).containsExactly("POR_REVISAR>APROBADO",
				"APROBADO>POR_REVISAR", "POR_REVISAR>RECHAZADO");
		assertThat(filas).extracting(f -> f.get("administrador_id")).containsOnly(adminId());
		assertThat(filas.toString()).doesNotContain("secreto").doesNotContain("1065123456").doesNotContain("Ana");
	}

	@Test
	void seGuardanRevisadoPorYRevisadoEnAlAprobarYAlRechazar() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		assertThat(pago("PAG-0001").get("revisado_por")).isEqualTo(adminId());
		assertThat(pago("PAG-0001").get("revisado_en")).isNotNull();
	}

	// --- errores

	@Test
	void pagoInexistenteDa404() throws Exception {
		revisar("PAG-9999", "Aprobado", null).andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("PAGO_NO_ENCONTRADO"))
				.andExpect(jsonPath("$.detail").value("El pago ya no existe."));
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "Pagado", "aprobado", "APROBADO", "" })
	void revisionAusenteOInvalidaDa400(String review) throws Exception {
		crearTitular();
		revisar("PAG-0001", review, null).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REVISION_INVALIDA"));
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   ", " \n\t " })
	void rechazarSinMotivoDa422(String motivo) throws Exception {
		crearTitular();
		revisar("PAG-0001", "Rechazado", motivo).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("MOTIVO_REQUERIDO"))
				.andExpect(jsonPath("$.detail").value("Indica el motivo del rechazo."));
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
	}

	@Test
	void aprobarConTitularMenorSinAutorizacionDa422YElPagoNoCambia() throws Exception {
		crear(menor());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("AUTORIZACION_MENOR_PENDIENTE"))
				.andExpect(jsonPath("$.detail").value(
						"El titular es menor de edad: falta la autorización firmada por su responsable."));
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
		assertThat(pago("PAG-0001").get("revisado_en")).isNull();

		jdbc.update("update inscripcion set autorizacion_menor = true");
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
	}

	@Test
	void aprobarDosPersonasEnParamoConUnCupoLibreDa409() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 2");
		crear(ReservaJson.conAcompanante());
		crear(ReservaJson.titular().set("document", "1065000111"));
		aprobarPagos("PER-0002");

		revisar("PAG-0001", "Aprobado", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SIN_CUPO_PARA_APROBAR"))
				.andExpect(jsonPath("$.detail").value("No hay cupos en el páramo para confirmar esta inscripción "
						+ "(2 en páramo, 1 libres). Cambia los cupos del páramo en Gestión de cupos."));
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("POR_REVISAR");
	}

	@Test
	void aprobarUnSegundoPagoDeUnaReservaQueYaOcupaCupoNoRevalidaCupos() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 2");
		crear(apartado());
		aprobarPagos("PER-0001");
		jdbc.update("""
				insert into pago (codigo, inscripcion_id, concepto, monto, fecha, revision, origen)
				values ('PAG-0099', (select id from inscripcion), 'PAGO_SALDO', 600000, current_date, 'POR_REVISAR',
				'PUBLICO')""");
		// Ahora el páramo quedaría corto, pero la reserva ya retiene sus cupos.
		jdbc.update("update configuracion_evento set cupos_paramo = 1");

		revisar("PAG-0099", "Aprobado", null).andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[1].review").value("Aprobado"));
	}

	@Test
	void reactivarUnaReservaRechazadaConDocumentoTomadoDa409() throws Exception {
		crearTitular();
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		enviar(ReservaJson.titular()).andExpect(status().isCreated());

		for (String review : List.of("Por revisar", "Aprobado")) {
			revisar("PAG-0001", review, null).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"))
					// Audiencia administrador: sin el aviso "Si no fuiste tú".
					.andExpect(jsonPath("$.detail").value("El documento 1065123456 ya tiene una reserva registrada."));
		}
		assertThat(pago("PAG-0001").get("revision")).isEqualTo("RECHAZADO");
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);
		assertThat(documentosActivos("PER-0001")).isEmpty();
	}

	@Test
	void rechazarODevolverAPorRevisarNuncaSeBloqueaPorMenorNiPorCupo() throws Exception {
		crear(menor());
		revisar("PAG-0001", "Rechazado", "x").andExpect(status().isOk());
		revisar("PAG-0001", "Por revisar", null).andExpect(status().isOk());

		// Reserva de 2 personas ya aprobada; el páramo se reduce y devolverla o rechazarla sigue permitido.
		crear(otra("1065000222", "1065000333"));
		aprobarPagos("PER-0002");
		jdbc.update("update configuracion_evento set cupos_paramo = 1");
		revisar("PAG-0002", "Por revisar", null).andExpect(status().isOk());
		revisar("PAG-0002", "Rechazado", "x").andExpect(status().isOk());
		revisar("PAG-0002", "Por revisar", null).andExpect(status().isOk());
	}

	// --- concurrencia

	private List<Integer> enParalelo(List<Callable3> tareas) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(tareas.size());
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (Callable3 t : tareas) {
				futuros.add(pool.submit(() -> {
					salida.await();
					return t.ejecutar();
				}));
			}
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> f : futuros) {
				estados.add(f.get(60, TimeUnit.SECONDS));
			}
			return estados;
		} finally {
			pool.shutdownNow();
		}
	}

	@FunctionalInterface
	private interface Callable3 {
		int ejecutar() throws Exception;
	}

	private int estado(String pago, String review, String motivo) throws Exception {
		return revisar(pago, review, motivo).andReturn().getResponse().getStatus();
	}

	@RepeatedTest(5)
	void dosAprobacionesSimultaneasPorElUltimoCupoDan200Y409() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 1");
		crear(ReservaJson.titular());
		crear(ReservaJson.titular().set("document", "1065000111"));
		adminId();

		List<Integer> estados = enParalelo(List.of(() -> estado("PAG-0001", "Aprobado", null),
				() -> estado("PAG-0002", "Aprobado", null)));

		assertThat(estados).containsExactlyInAnyOrder(200, 409);
		assertThat(ocupados()).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from pago where revision = 'APROBADO'", Integer.class))
				.isEqualTo(1);
	}

	@RepeatedTest(5)
	void dosRevisionesSimultaneasDelMismoPagoDejanElEstadoCoherente() throws Exception {
		crear(ReservaJson.conAcompanante());
		adminId();

		List<Integer> estados = enParalelo(List.of(() -> estado("PAG-0001", "Rechazado", "No aparece"),
				() -> estado("PAG-0001", "Aprobado", null), () -> estado("PAG-0001", "Por revisar", null)));

		assertThat(estados).containsOnly(200);
		boolean rechazado = "RECHAZADO".equals(pago("PAG-0001").get("revision"));
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(!rechazado);
		assertThat(documentosActivos("PER-0001")).hasSize(rechazado ? 0 : 2);
		assertThat(pago("PAG-0001").get("motivo_rechazo") != null).isEqualTo(rechazado);
	}

}
