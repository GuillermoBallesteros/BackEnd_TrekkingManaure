package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

/** HU-N10 con PostgreSQL 18 real: autorización firmada del responsable del titular menor. */
class AutorizacionMenorTest extends ReservaPublicaBase {

	private void crear(ReservaJson r) throws Exception {
		enviar(r).andExpect(status().isCreated());
	}

	private ReservaJson menor() {
		return ReservaJson.titular().set("documentType", "TI").set("document", "1234567890")
				.set("birthDate", "2012-03-01").set("emergencyContact.relationship", "Madre");
	}

	private boolean guardada(String codigo) {
		return jdbc.queryForObject("select autorizacion_menor from inscripcion where codigo = ?", Boolean.class,
				codigo);
	}

	@Test
	void recibidaTrueDa200YLuegoAprobarUnPagoFunciona() throws Exception {
		crear(menor());
		autorizacionMenor("PER-0001", "{\"received\":true}").andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("PER-0001"))
				.andExpect(jsonPath("$.guardianAuthorized").value(true));
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
	}

	@Test
	void recibidaFalseDa200YAprobarDa422() throws Exception {
		crear(menor());
		autorizacionMenor("PER-0001", "{\"received\":true}").andExpect(status().isOk());
		autorizacionMenor("PER-0001", "{\"received\":false}").andExpect(status().isOk())
				.andExpect(jsonPath("$.guardianAuthorized").value(false));
		revisar("PAG-0001", "Aprobado", null).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("AUTORIZACION_MENOR_PENDIENTE"));
	}

	@Test
	void marcarlaNoApruebaNiCambiaNingunPago() throws Exception {
		crear(menor());
		Map<String, Object> antes = jdbc.queryForMap("select * from pago where codigo = 'PAG-0001'");
		autorizacionMenor("PER-0001", "{\"received\":true}").andExpect(status().isOk())
				.andExpect(jsonPath("$.payments[0].review").value("Por revisar"));
		assertThat(jdbc.queryForMap("select * from pago where codigo = 'PAG-0001'")).isEqualTo(antes);
		assertThat(contar("pago")).isEqualTo(1);
	}

	@Test
	void seAceptaSobreUnTitularMayorDeEdad() throws Exception {
		crear(ReservaJson.titular());
		autorizacionMenor("PER-0001", "{\"received\":true}").andExpect(status().isOk())
				.andExpect(jsonPath("$.guardianAuthorized").value(true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"received\":null}", "{\"received\":\"true\"}", "{\"received\":1}",
			"{\"received\":\"si\"}", "" })
	void recibidaAusenteONoBooleanaDa400(String cuerpo) throws Exception {
		crear(menor());
		autorizacionMenor("PER-0001", cuerpo).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
		assertThat(guardada("PER-0001")).isFalse();
	}

	@Test
	void reservaInexistenteDa404() throws Exception {
		autorizacionMenor("PER-9999", "{\"received\":true}").andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESERVA_NO_ENCONTRADA"))
				.andExpect(jsonPath("$.detail").value("La inscripción ya no existe."));
	}

	@Test
	void sinTokenDa401() throws Exception {
		crear(menor());
		mvc.perform(put("/api/v1/admin/reservas/PER-0001/autorizacion-menor").contentType(MediaType.APPLICATION_JSON)
				.content("{\"received\":true}")).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
		assertThat(guardada("PER-0001")).isFalse();
	}

	@Test
	void seAuditaConElCodigoDeLaReservaYElValor() throws Exception {
		crear(menor());
		autorizacionMenor("PER-0001", "{\"received\":true}").andExpect(status().isOk());
		Map<String, Object> a = jdbc.queryForMap("""
				select entidad, entidad_ref, detalle, administrador_id from auditoria
				where accion = 'FIJAR_AUTORIZACION_MENOR'""");
		assertThat(a.get("entidad")).isEqualTo("RESERVA");
		assertThat(a.get("entidad_ref")).isEqualTo("PER-0001");
		assertThat(a.get("detalle")).isEqualTo("received=true");
		assertThat(a.get("administrador_id")).isEqualTo(adminId());
	}

	@Test
	void persisteYLaListaDevuelveElValorGuardado() throws Exception {
		crear(menor());
		autorizacionMenor("PER-0001", "{\"received\":true}").andExpect(status().isOk());
		assertThat(guardada("PER-0001")).isTrue();
		mvc.perform(get("/api/v1/admin/reservas").header("Authorization", "Bearer " + tokenAdmin()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.registrations[0].guardianAuthorized").value(true));
	}

}
