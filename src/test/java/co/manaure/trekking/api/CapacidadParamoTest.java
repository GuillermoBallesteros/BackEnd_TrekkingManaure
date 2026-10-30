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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

/** HU-N11 con PostgreSQL 18 real: cupos de la carrera y cupos del páramo (RN-17). */
class CapacidadParamoTest extends ConfiguracionAdminBase {

	private static final String RUTA = "/api/v1/admin/capacidad";

	private int guardada() {
		return jdbc.queryForObject("select cupos_paramo from configuracion_evento", Integer.class);
	}

	private int carreraGuardada() {
		return jdbc.queryForObject("select cupos_carrera from configuracion_evento", Integer.class);
	}

	private static String cuerpo(int carrera, int paramo) {
		return "{\"capacity\":" + carrera + ",\"paramo\":" + paramo + "}";
	}

	private void esperar422(String cuerpo, String mensaje) throws Exception {
		enviarPut(RUTA, cuerpo).andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("CAPACIDAD_INVALIDA")).andExpect(jsonPath("$.detail").value(mensaje));
	}

	@Test
	void ochentaConDoceOcupadosDa200YDisponibilidadMuestra68() throws Exception {
		ocupar(6);
		enviarPut(RUTA, cuerpo(70, 80)).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(70))
				.andExpect(jsonPath("$.paramo").value(80));
		mvc.perform(get("/api/v1/disponibilidad")).andExpect(status().isOk())
				.andExpect(jsonPath("$.capacity").value(70)).andExpect(jsonPath("$.occupied").value(6))
				.andExpect(jsonPath("$.available").value(64)).andExpect(jsonPath("$.paramo.capacity").value(80))
				.andExpect(jsonPath("$.paramo.occupied").value(12)).andExpect(jsonPath("$.paramo.available").value(68));
		assertThat(carreraGuardada()).isEqualTo(70);
		assertThat(guardada()).isEqualTo(80);
	}

	@Test
	void igualALosOcupadosDa200() throws Exception {
		ocupar(6);
		enviarPut(RUTA, cuerpo(6, 12)).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(6))
				.andExpect(jsonPath("$.paramo").value(12));
		mvc.perform(get("/api/v1/disponibilidad")).andExpect(jsonPath("$.available").value(0))
				.andExpect(jsonPath("$.paramo.available").value(0));
	}

	@Test
	void menorQueLosOcupadosDa422ConElNumero() throws Exception {
		ocupar(6);
		esperar422(cuerpo(60, 10), "Los cupos del páramo no pueden ser menos que los 12 cupos ya ocupados.");
		assertThat(guardada()).isEqualTo(60);
	}

	@Test
	void carreraMenorQueLasReservasActivasDa422ConElNumero() throws Exception {
		ocupar(6);
		esperar422(cuerpo(5, 60), "Los cupos de la carrera no pueden ser menos que las 6 reservas activas.");
		assertThat(carreraGuardada()).isEqualTo(60);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "-5" })
	void sinOcupadosCeroONegativoDa422(String valor) throws Exception {
		esperar422("{\"capacity\":60,\"paramo\":" + valor + "}", "Debe haber al menos 1 cupo en el páramo.");
		esperar422("{\"capacity\":" + valor + ",\"paramo\":60}", "Debe haber al menos 1 cupo en la carrera.");
		assertThat(guardada()).isEqualTo(60);
		assertThat(carreraGuardada()).isEqualTo(60);
	}

	@Test
	void sinOcupadosUnoSeAcepta() throws Exception {
		enviarPut(RUTA, cuerpo(1, 1)).andExpect(status().isOk()).andExpect(jsonPath("$.capacity").value(1))
				.andExpect(jsonPath("$.paramo").value(1));
	}

	@ParameterizedTest
	@ValueSource(strings = { "10.5", "10.0", "null", "\"abc\"", "\"10\"", "true", "99999999999" })
	void decimalNuloOTextoDa422DeEnteroRequerido(String valor) throws Exception {
		esperar422("{\"capacity\":" + valor + ",\"paramo\":60}", "Los cupos de la carrera deben ser un número entero.");
		esperar422("{\"capacity\":60,\"paramo\":" + valor + "}", "Los cupos del páramo deben ser un número entero.");
		assertThat(guardada()).isEqualTo(60);
		assertThat(carreraGuardada()).isEqualTo(60);
	}

	@ParameterizedTest
	@ValueSource(strings = { "{}", "{\"paramo\":60}" })
	void sinCuposDeLaCarreraDa422DeEnteroRequerido(String cuerpo) throws Exception {
		esperar422(cuerpo, "Los cupos de la carrera deben ser un número entero.");
	}

	@Test
	void sinCuposDelParamoDa422DeEnteroRequerido() throws Exception {
		esperar422("{\"capacity\":60}", "Los cupos del páramo deben ser un número entero.");
	}

	@Test
	void unaReservaSimultaneaNoPermiteDejarLaCapacidadPorDebajoDeLoOcupado() throws Exception {
		ocupar(5); // 10 cupos ocupados
		String token = tokenAdmin();
		// Reserva nueva con 2 en páramo: aparta sus cupos del páramo al crearse.
		String nueva = ReservaJson.conAcompanante().set("document", "1065000555").set("email", "n@example.com")
				.set("companion.document", "1065999555").set("companion.email", "nc@example.com").json();

		// Sin concurrencia el resultado depende del orden; con ambos en paralelo, en cualquier orden el estado final
		// debe cumplir capacidad >= ocupados.
		CompletableFuture<Integer> bajar = CompletableFuture.supplyAsync(() -> estado(
				put(RUTA).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
						.content(cuerpo(60, 10))));
		CompletableFuture<Integer> reservar = CompletableFuture.supplyAsync(() -> estado(
				post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON).content(nueva)));
		int estadoBajar = bajar.get(60, TimeUnit.SECONDS);
		int estadoReservar = reservar.get(60, TimeUnit.SECONDS);

		int ocupados = jdbc.queryForObject("""
				select count(*) from inscripcion_participante ip join estadia e on e.clave = ip.estadia and e.ocupa_cupo
				join inscripcion i on i.id = ip.inscripcion_id and i.activa""", Integer.class);
		assertThat(guardada()).isGreaterThanOrEqualTo(ocupados);
		// Exactamente uno gana: o baja a 10 (y la reserva da 409 sin cupos) o reserva (y bajar da 422).
		if (estadoBajar == 200) {
			assertThat(estadoReservar).isEqualTo(409);
			assertThat(ocupados).isEqualTo(10);
		} else {
			assertThat(estadoBajar).isEqualTo(422);
			assertThat(estadoReservar).isEqualTo(201);
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
		enviarPut(RUTA, cuerpo(70, 80)).andExpect(status().isOk());
		Map<String, Object> c = jdbc
				.queryForMap("select actualizado_por, actualizado_en from configuracion_evento");
		assertThat(c.get("actualizado_por")).isEqualTo(adminId());
		assertThat(c.get("actualizado_en")).isNotNull();
		Map<String, Object> a = jdbc.queryForMap("""
				select entidad, detalle, administrador_id from auditoria where accion = 'FIJAR_CAPACIDAD'""");
		assertThat(a.get("entidad")).isEqualTo("CONFIGURACION");
		assertThat(a.get("detalle")).isEqualTo("carrera anterior=60, nuevo=70; paramo anterior=60, nuevo=80");
		assertThat(a.get("administrador_id")).isEqualTo(adminId());
	}

	@Test
	void unaCapacidadRechazadaNoAudita() throws Exception {
		enviarPut(RUTA, cuerpo(60, 0)).andExpect(status().isUnprocessableEntity());
		assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = 'FIJAR_CAPACIDAD'",
				Integer.class)).isZero();
	}

	@Test
	void sinTokenDa401() throws Exception {
		putSinToken(RUTA, cuerpo(70, 80)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
		assertThat(guardada()).isEqualTo(60);
	}

	@Test
	void sinCuerpoDa422DeEnteroRequerido() throws Exception {
		esperar422("", "Los cupos de la carrera deben ser un número entero.");
	}

}
