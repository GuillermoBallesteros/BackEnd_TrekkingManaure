package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** HU-N12 con PostgreSQL 18 real: fecha de cierre de inscripciones (RN-09, RN-10). */
class FechaCierreTest extends ConfiguracionAdminBase {

	private static final String RUTA = "/api/v1/admin/fecha-cierre";

	private String guardada() {
		return jdbc.queryForObject("select fecha_cierre::text from configuracion_evento", String.class);
	}

	private void esperar422(String cuerpo, String mensaje) throws Exception {
		enviarPut(RUTA, cuerpo).andExpect(status().isUnprocessableEntity())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("FECHA_CIERRE_INVALIDA"))
				.andExpect(jsonPath("$.detail").value(mensaje));
		assertThat(guardada()).isEqualTo("2026-11-09");
	}

	private ReservaJson apartado(String fecha) {
		return ReservaJson.titular().set("plan", "apartado").set("commitmentDate", fecha);
	}

	@Test
	void fechaValidaDa200YConfigYDisponibilidadDevuelvenLaNueva() throws Exception {
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-11-12\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaCierre").value("2026-11-12"));
		mvc.perform(get("/api/v1/config")).andExpect(status().isOk())
				.andExpect(jsonPath("$.evento.fechaCierre").value("2026-11-12"));
		mvc.perform(get("/api/v1/disponibilidad")).andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaCierre").value("2026-11-12"));
		assertThat(guardada()).isEqualTo("2026-11-12");
	}

	@Test
	void igualALaAperturaDa200() throws Exception {
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-10-28\"}").andExpect(status().isOk())
				.andExpect(jsonPath("$.fechaCierre").value("2026-10-28"));
	}

	@Test
	void fechaPasadaPeroNoAnteriorALaAperturaDa200YLasNuevasReservasDan409() throws Exception {
		reloj.fijar(Instant.parse("2026-11-02T15:00:00Z"));
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-10-30\"}").andExpect(status().isOk());
		enviar(ReservaJson.titular()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSCRIPCIONES_CERRADAS"));
		assertThat(contar("inscripcion")).isZero();
	}

	@Test
	void anteriorALaAperturaDa422() throws Exception {
		esperar422("{\"fechaCierre\":\"2026-10-27\"}",
				"La fecha de cierre no puede ser anterior a la apertura de inscripciones.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-11-28", "2026-11-29", "2027-01-01" })
	void igualOPosteriorAlPrimerDiaDelEventoDa422(String fecha) throws Exception {
		esperar422("{\"fechaCierre\":\"" + fecha + "\"}", "La fecha de cierre debe ser antes del evento.");
	}

	@Test
	void laVisperaDelEventoSeAcepta() throws Exception {
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-11-27\"}").andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"fechaCierre\":\"12/11/2026\"}", "{\"fechaCierre\":\"2026-02-30\"}", "{}",
			"{\"fechaCierre\":null}", "{\"fechaCierre\":20261112}", "{\"fechaCierre\":\"2026-11-12T00:00:00\"}",
			"{\"fechaCierre\":\"\"}", "" })
	void formatoInvalidoInexistenteOAusenteDa422(String cuerpo) throws Exception {
		esperar422(cuerpo, "Elige una fecha de cierre válida.");
	}

	@Test
	void trasMoverlaElApartadoAceptaFechasHastaLaNuevaYRechazaLasPosteriores() throws Exception {
		// Con el cierre original (2026-11-09) 2026-11-12 se rechaza.
		enviar(apartado("2026-11-12")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("FECHA_COMPROMISO_INVALIDA"));

		enviarPut(RUTA, "{\"fechaCierre\":\"2026-11-12\"}").andExpect(status().isOk());
		enviar(apartado("2026-11-13")).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("FECHA_COMPROMISO_INVALIDA"));
		enviar(apartado("2026-11-12")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.commitmentDate").value("2026-11-12"));

		// Adelantarla rechaza lo que antes cabía.
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-11-05\"}").andExpect(status().isOk());
		enviar(ReservaJson.titular().set("document", "1065000222").set("email", "otro@example.com")
				.set("plan", "apartado").set("commitmentDate", "2026-11-09"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("FECHA_COMPROMISO_INVALIDA"));
	}

	@Test
	void guardaActualizadoPorYActualizadoEnYAudita() throws Exception {
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-11-12\"}").andExpect(status().isOk());
		Map<String, Object> c = jdbc
				.queryForMap("select actualizado_por, actualizado_en from configuracion_evento");
		assertThat(c.get("actualizado_por")).isEqualTo(adminId());
		assertThat(c.get("actualizado_en")).isNotNull();
		Map<String, Object> a = jdbc.queryForMap("""
				select entidad, detalle, administrador_id from auditoria where accion = 'FIJAR_FECHA_CIERRE'""");
		assertThat(a.get("entidad")).isEqualTo("CONFIGURACION");
		assertThat(a.get("detalle")).isEqualTo("anterior=2026-11-09, nuevo=2026-11-12");
		assertThat(a.get("administrador_id")).isEqualTo(adminId());
	}

	@Test
	void unaFechaRechazadaNoAudita() throws Exception {
		enviarPut(RUTA, "{\"fechaCierre\":\"2026-11-28\"}").andExpect(status().isUnprocessableEntity());
		assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = 'FIJAR_FECHA_CIERRE'",
				Integer.class)).isZero();
	}

	@Test
	void sinTokenDa401() throws Exception {
		putSinToken(RUTA, "{\"fechaCierre\":\"2026-11-12\"}").andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
		assertThat(guardada()).isEqualTo("2026-11-09");
	}

}
