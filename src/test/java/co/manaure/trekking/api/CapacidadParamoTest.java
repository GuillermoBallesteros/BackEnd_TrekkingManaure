package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** HU-N11 con PostgreSQL 18 real: capacidad del páramo (RN-17). */
class CapacidadParamoTest extends ConfiguracionAdminBase {

	private static final String RUTA = "/api/v1/admin/capacidad";

	private int guardada() {
		return jdbc.queryForObject("select cupos_paramo from configuracion_evento", Integer.class);
	}

	private void esperar422(String cuerpo, String mensaje) throws Exception {
		enviarPut(RUTA, cuerpo).andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("CAPACIDAD_INVALIDA")).andExpect(jsonPath("$.detail").value(mensaje));
	}

	@Test
	void ochentaConDoceOcupadosDa200YDisponibilidadMuestra68() throws Exception {
		ocupar(6);
		enviarPut(RUTA, "{\"capacity\":80}").andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(80));
		mvc.perform(get("/api/v1/disponibilidad")).andExpect(status().isOk())
				.andExpect(jsonPath("$.capacity").value(80)).andExpect(jsonPath("$.occupied").value(12))
				.andExpect(jsonPath("$.available").value(68));
		assertThat(guardada()).isEqualTo(80);
	}

	@Test
	void igualALosOcupadosDa200() throws Exception {
		ocupar(6);
		enviarPut(RUTA, "{\"capacity\":12}").andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(12));
		mvc.perform(get("/api/v1/disponibilidad")).andExpect(jsonPath("$.available").value(0));
	}

	@Test
	void menorQueLosOcupadosDa422ConElNumero() throws Exception {
		ocupar(6);
		esperar422("{\"capacity\":10}", "Los cupos del páramo no pueden ser menos que los 12 ya ocupados.");
		assertThat(guardada()).isEqualTo(60);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5" })
	void sinOcupadosCeroONegativoDa422(String valor) throws Exception {
		esperar422("{\"capacity\":" + valor + "}", "Debe haber al menos 1 cupo en el páramo.");
		assertThat(guardada()).isEqualTo(60);
	}

	@Test
	void sinOcupadosUnoSeAcepta() throws Exception {
		enviarPut(RUTA, "{\"capacity\":1}").andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"capacity\":10.5}", "{\"capacity\":10.0}", "{}", "{\"capacity\":null}",
			"{\"capacity\":\"abc\"}", "{\"capacity\":\"10\"}", "{\"capacity\":true}", "{\"capacity\":99999999999}" })
	void decimalAusenteOTextoDa422DeEnteroRequerido(String cuerpo) throws Exception {
		esperar422(cuerpo, "Los cupos del páramo deben ser un número entero.");
		assertThat(guardada()).isEqualTo(60);
	}

	@Test
	void unaAprobacionSimultaneaNoPermiteDejarLaCapacidadPorDebajoDeLoOcupado() throws Exception {
		ocupar(5); // 10 ocupados
		// Reserva nueva con 2 en páramo, pendiente de aprobar.
		enviar(ReservaJson.conAcompanante().set("document", "1065000555").set("email", "n@example.com")
				.set("companion.document", "1065999555").set("companion.email", "nc@example.com"))
				.andExpect(status().isCreated());
		String pago = jdbc.queryForObject("select codigo from pago where revision = 'POR_REVISAR'", String.class);
		String token = tokenAdmin();

		// Sin concurrencia el resultado depende del orden; con ambos en paralelo, en cualquier orden el estado final
		// debe cumplir capacidad >= ocupados.
		CompletableFuture<Integer> bajar = CompletableFuture.supplyAsync(() -> estado(
				put(RUTA).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
						.content("{\"capacity\":10}")));
		CompletableFuture<Integer> aprobar = CompletableFuture.supplyAsync(() -> estado(
				put("/api/v1/admin/pagos/" + pago + "/revision").header("Authorization", "Bearer " + token)
						.contentType(MediaType.APPLICATION_JSON).content("{\"review\":\"Aprobado\"}")));
		int estadoBajar = bajar.get(60, TimeUnit.SECONDS);
		int estadoAprobar = aprobar.get(60, TimeUnit.SECONDS);

		int ocupados = jdbc.queryForObject("""
				select count(*) from inscripcion_participante ip join estadia e on e.clave = ip.estadia and e.ocupa_cupo
				where exists (select 1 from pago p where p.inscripcion_id = ip.inscripcion_id and p.revision = 'APROBADO')""",
				Integer.class);
		assertThat(guardada()).isGreaterThanOrEqualTo(ocupados);
		// Exactamente uno gana: o baja a 10 (y aprobar da 409 por falta de cupo) o aprueba (y bajar da 422).
		if (estadoBajar == 200) {
			assertThat(estadoAprobar).isEqualTo(409);
			assertThat(ocupados).isEqualTo(10);
		} else {
			assertThat(estadoBajar).isEqualTo(422);
			assertThat(estadoAprobar).isEqualTo(200);
			assertThat(ocupados).isEqualTo(12);
		}
	}

	private int estado(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) {
		try {
			return mvc.perform(req).andReturn().getResponse().getStatus();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	@Test
	void guardaActualizadoPorYActualizadoEnYAudita() throws Exception {
		enviarPut(RUTA, "{\"capacity\":80}").andExpect(status().isOk());
		Map<String, Object> c = jdbc
				.queryForMap("select actualizado_por, actualizado_en from configuracion_evento");
		assertThat(c.get("actualizado_por")).isEqualTo(adminId());
		assertThat(c.get("actualizado_en")).isNotNull();
		Map<String, Object> a = jdbc.queryForMap("""
				select entidad, detalle, administrador_id from auditoria where accion = 'FIJAR_CAPACIDAD'""");
		assertThat(a.get("entidad")).isEqualTo("CONFIGURACION");
		assertThat(a.get("detalle")).isEqualTo("anterior=60, nuevo=80");
		assertThat(a.get("administrador_id")).isEqualTo(adminId());
	}

	@Test
	void unaCapacidadRechazadaNoAudita() throws Exception {
		enviarPut(RUTA, "{\"capacity\":0}").andExpect(status().isUnprocessableEntity());
		assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = 'FIJAR_CAPACIDAD'",
				Integer.class)).isZero();
	}

	@Test
	void sinTokenDa401() throws Exception {
		putSinToken(RUTA, "{\"capacity\":80}").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
		assertThat(guardada()).isEqualTo(60);
	}

	@Test
	void sinCuerpoDa422DeEnteroRequerido() throws Exception {
		esperar422("", "Los cupos del páramo deben ser un número entero.");
	}

}
