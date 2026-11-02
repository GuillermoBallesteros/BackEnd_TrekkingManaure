package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** RN-25 y RN-26 con PostgreSQL real: ampliar el plazo o rechazar una reserva vencida; pagar el saldo la deja pagada. */
class VencimientoTest extends ReservaPublicaBase {

	private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

	/** Mediodía en Bogotá del día indicado de 2026. */
	private void dia(int mes, int dia) {
		reloj.fijar(ZonedDateTime.of(2026, mes, dia, 12, 0, 0, 0, BOGOTA).toInstant());
	}

	/** Titular con plan apartado (total 350.000), compromiso el 9 de noviembre y la separación aprobada. */
	private void reservaConSeparacionAprobada() throws Exception {
		enviar(ReservaJson.titular().set("plan", "apartado").set("commitmentDate", "2026-11-09"))
				.andExpect(status().isCreated());
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
	}

	/** La misma reserva, ya vencida: hoy es 10 de noviembre. */
	private void reservaVencida() throws Exception {
		reservaConSeparacionAprobada();
		dia(11, 10);
	}

	private ResultActions ampliar(String codigo, String cuerpoJson) throws Exception {
		return mvc.perform(put("/api/v1/admin/reservas/" + codigo + "/plazo")
				.header("Authorization", "Bearer " + tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoJson));
	}

	private ResultActions rechazar(String codigo) throws Exception {
		return mvc.perform(post("/api/v1/admin/reservas/" + codigo + "/rechazo")
				.header("Authorization", "Bearer " + tokenAdmin()));
	}

	private Map<String, Object> inscripcion(String codigo) {
		return jdbc.queryForMap("select * from inscripcion where codigo = ?", codigo);
	}

	private int cuposParamo() throws Exception {
		String json = mvc.perform(get("/api/v1/disponibilidad")).andReturn().getResponse().getContentAsString();
		return new tools.jackson.databind.json.JsonMapper().readTree(json).get("paramo").get("occupied").asInt();
	}

	private List<Map<String, Object>> auditoria(String accion) {
		return jdbc.queryForList("select * from auditoria where accion = ?", accion);
	}

	// --- ampliar plazo

	@Test
	void sinTokenDa401() throws Exception {
		reservaVencida();
		mvc.perform(put("/api/v1/admin/reservas/PER-0001/plazo").contentType(MediaType.APPLICATION_JSON)
				.content("{\"days\":3}")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/admin/reservas/PER-0001/rechazo")).andExpect(status().isUnauthorized());
	}

	@Test
	void ampliarElPlazoDeUnaVencidaLoFijaEnHoyMasLosDiasYVuelveASaldoPendiente() throws Exception {
		reservaVencida();

		ampliar("PER-0001", "{\"days\":5}").andExpect(status().isOk())
				.andExpect(jsonPath("$.commitmentDate").value("2026-11-15"))
				.andExpect(jsonPath("$.rejected").value(false))
				.andExpect(jsonPath("$.payments[0].review").value("Aprobado"));
		assertThat(inscripcion("PER-0001").get("fecha_compromiso").toString()).isEqualTo("2026-11-15");
		assertThat(auditoria("AMPLIAR_PLAZO")).singleElement().satisfies(f -> {
			assertThat(f.get("entidad")).isEqualTo("RESERVA");
			assertThat(f.get("entidad_ref")).isEqualTo("PER-0001");
			assertThat(f.get("detalle")).isEqualTo("dias=5 hasta=2026-11-15");
			assertThat(f.get("administrador_id")).isEqualTo(adminId());
		});
		// Ya no está vencida: no se vuelve a ampliar ni se rechaza.
		ampliar("PER-0001", "{\"days\":1}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESERVA_NO_VENCIDA"));
		rechazar("PER-0001").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESERVA_NO_VENCIDA"));
	}

	@Test
	void ampliarPermiteUnDiaYNoTieneTopePorLaFechaDeCierre() throws Exception {
		reservaConSeparacionAprobada();
		dia(11, 25);
		ampliar("PER-0001", "{\"days\":1}").andExpect(status().isOk())
				.andExpect(jsonPath("$.commitmentDate").value("2026-11-26"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"days\":0}", "{\"days\":6}", "{\"days\":-1}", "{\"days\":\"3\"}", "{\"days\":2.5}",
			"{\"days\":null}", "{}" })
	void diasFueraDeUnoACincoONoEnterosDan422(String cuerpo) throws Exception {
		reservaVencida();
		ampliar("PER-0001", cuerpo).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("PLAZO_INVALIDO"))
				.andExpect(jsonPath("$.detail").value("Elige entre 1 y 5 días."));
		assertThat(inscripcion("PER-0001").get("fecha_compromiso").toString()).isEqualTo("2026-11-09");
	}

	@Test
	void ampliarUnaReservaQueNoEstaVencidaDa409() throws Exception {
		reservaConSeparacionAprobada();
		dia(11, 9);
		ampliar("PER-0001", "{\"days\":3}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESERVA_NO_VENCIDA"))
				.andExpect(jsonPath("$.detail").value(
						"Solo se puede hacer con una reserva vencida (está en Saldo pendiente)."));
	}

	@Test
	void reservaInexistenteDa404() throws Exception {
		ampliar("PER-9999", "{\"days\":3}").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVA_NO_ENCONTRADA"));
		rechazar("PER-9999").andExpect(status().isNotFound());
	}

	// --- pagar el saldo

	@Test
	void unaReservaVencidaNoRecibePagosHastaQueSeAmplieElPlazo() throws Exception {
		reservaVencida();
		pagoManual("PER-0001", "{\"amount\":300000}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESERVA_VENCIDA"))
				.andExpect(jsonPath("$.detail").value("La reserva está vencida: amplía el plazo antes de registrar un pago."));
		assertThat(jdbc.queryForObject("select count(*) from pago", Integer.class)).isEqualTo(1);

		// Con el plazo ampliado vuelve a admitir pagos y el saldo completo la deja pagada.
		ampliar("PER-0001", "{\"days\":3}").andExpect(status().isOk());
		pagoManual("PER-0001", "{\"amount\":300000}").andExpect(status().isCreated());
		ampliar("PER-0001", "{\"days\":3}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Solo se puede hacer con una reserva vencida (está en Pagado)."));
	}

	// --- rechazar

	@Test
	void rechazarUnaVencidaLiberaDocumentoYCuposYConservaElPagoAprobado() throws Exception {
		reservaVencida();
		assertThat(cuposParamo()).isEqualTo(1);

		rechazar("PER-0001").andExpect(status().isOk()).andExpect(jsonPath("$.rejected").value(true))
				.andExpect(jsonPath("$.payments[0].review").value("Aprobado"));
		Map<String, Object> fila = inscripcion("PER-0001");
		assertThat(fila.get("activa")).isEqualTo(false);
		assertThat(fila.get("rechazada_en")).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from inscripcion_participante where documento_activo is not null",
				Integer.class)).isZero();
		assertThat(cuposParamo()).isZero();
		assertThat(jdbc.queryForObject("select revision from pago where codigo = 'PAG-0001'", String.class))
				.isEqualTo("APROBADO");
		assertThat(auditoria("RECHAZAR_RESERVA")).singleElement()
				.satisfies(f -> assertThat(f.get("detalle")).isEqualTo("VENCIDO>RECHAZADO"));
	}

	@Test
	void unaReservaRechazadaNoAdmitePagosNiRevisionesNiOtroRechazo() throws Exception {
		reservaVencida();
		jdbc.update("""
				insert into pago (codigo, inscripcion_id, concepto, monto, fecha, revision, motivo_rechazo, origen,
				revisado_en) values ('PAG-0099', (select id from inscripcion), 'PAGO_SALDO', 300000, current_date,
				'RECHAZADO', 'x', 'PUBLICO', now())""");
		rechazar("PER-0001").andExpect(status().isOk());

		pagoManual("PER-0001", "{\"amount\":300000}").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESERVA_RECHAZADA"));
		revisar("PAG-0099", "Aprobado", null).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("RESERVA_RECHAZADA"));
		rechazar("PER-0001").andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("Solo se puede hacer con una reserva vencida (está en Rechazado)."));
		ampliar("PER-0001", "{\"days\":3}").andExpect(status().isConflict());
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(false);
	}

	@Test
	void conUnPagoPorRevisarNoSeRechazaLaReserva() throws Exception {
		reservaVencida();
		jdbc.update("""
				insert into pago (codigo, inscripcion_id, concepto, monto, fecha, revision, origen)
				values ('PAG-0099', (select id from inscripcion), 'PAGO_SALDO', 300000, current_date, 'POR_REVISAR',
				'PUBLICO')""");

		rechazar("PER-0001").andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("PAGO_POR_REVISAR_PENDIENTE"));
		assertThat(inscripcion("PER-0001").get("activa")).isEqualTo(true);
		assertThat(inscripcion("PER-0001").get("rechazada_en")).isNull();
	}

	@Test
	void laListaDelPanelMarcaLaReservaRechazada() throws Exception {
		reservaVencida();
		rechazar("PER-0001").andExpect(status().isOk());
		mvc.perform(get("/api/v1/admin/reservas").header("Authorization", "Bearer " + tokenAdmin()))
				.andExpect(status().isOk()).andExpect(jsonPath("$.registrations[0].rejected").value(true))
				.andExpect(jsonPath("$.occupied").value(0));
	}

}
