package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import co.manaure.trekking.TestcontainersConfiguration;
import co.manaure.trekking.pago.ConceptoPago;
import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.pago.PagoRepository;
import co.manaure.trekking.persona.Persona;
import co.manaure.trekking.persona.PersonaRepository;
import co.manaure.trekking.reserva.GeneradorCodigos;
import co.manaure.trekking.reserva.Inscripcion;
import co.manaure.trekking.reserva.InscripcionParticipante;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import co.manaure.trekking.reserva.InscripcionRepository;
import co.manaure.trekking.reserva.PlanPago;
import co.manaure.trekking.reserva.RolParticipante;

/** HU-N03: /config y /disponibilidad contra PostgreSQL real, sin autenticacion. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublicoControllerIntegrationTest {

	private static final AtomicInteger SEQ = new AtomicInteger(10000);

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired GeneradorCodigos codigos;
	@Autowired PersonaRepository personas;
	@Autowired InscripcionRepository inscripciones;
	@Autowired InscripcionParticipanteRepository participantes;
	@Autowired PagoRepository pagos;

	@BeforeEach
	@AfterEach
	void limpiar() {
		for (String tabla : new String[] { "pago", "inscripcion_participante", "inscripcion", "persona" }) {
			jdbc.update("delete from " + tabla);
		}
		jdbc.update("update configuracion_evento set cupos_carrera = 60, cupos_paramo = 60");
	}

	/** Crea una inscripcion activa con titular y, si se pide, acompanante; devuelve el pago aun por revisar. */
	private Pago reserva(String estadiaTitular, String estadiaAcompanante) {
		Inscripcion insc = inscripciones.save(new Inscripcion(codigos.siguienteCodigoInscripcion(), PlanPago.TOTAL, null,
				350000, 50000, 150000, 350000, 150000, 200000, 45000, 350000, LocalDate.of(2026, 10, 28)));
		String docTitular = doc();
		participantes.save(new InscripcionParticipante(insc, persona(docTitular), RolParticipante.TITULAR,
				estadiaTitular, true, "M", docTitular));
		if (estadiaAcompanante != null) {
			String docAcomp = doc();
			participantes.save(new InscripcionParticipante(insc, persona(docAcomp), RolParticipante.ACOMPANANTE,
					estadiaAcompanante, false, null, docAcomp));
		}
		return pagos.save(Pago.publico(codigos.siguienteCodigoPago(), insc, ConceptoPago.PAGO_TOTAL, 350000,
				LocalDate.of(2026, 10, 28), java.time.OffsetDateTime.now()));
	}

	private String doc() {
		return String.valueOf(70000000 + SEQ.incrementAndGet());
	}

	private Persona persona(String documento) {
		return personas.save(new Persona("ANA", "PRUEBA", "CC", documento, LocalDate.of(1990, 5, 14), "Femenino", "CO",
				"3001234567", "ana@example.com", "Valledupar", "Nueva EPS", null));
	}

	/** Simula la aprobacion (HU-N08 aun no existe) por insercion directa del estado. */
	private void aprobar(Pago pago) {
		jdbc.update("update pago set revision = 'APROBADO', revisado_en = now() where id = ?", pago.getId());
	}

	private ResultActions disponibilidad() throws Exception {
		return mvc.perform(get("/api/v1/disponibilidad")).andExpect(status().isOk());
	}

	@Test
	void configInicial() throws Exception {
		mvc.perform(get("/api/v1/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.evento.nombre").value("Trekking Desafío Perijá 13K"))
				.andExpect(jsonPath("$.evento.fechas[0]").value("2026-11-28"))
				.andExpect(jsonPath("$.evento.fechas[1]").value("2026-11-29"))
				.andExpect(jsonPath("$.evento.fechaAperturaInscripciones").value("2026-10-28"))
				.andExpect(jsonPath("$.evento.fechaCierre").value("2026-11-09"));
	}

	@Test
	void configPrecios() throws Exception {
		mvc.perform(get("/api/v1/config"))
				.andExpect(jsonPath("$.precios.total").value(350000))
				.andExpect(jsonPath("$.precios.inscripcion").value(50000))
				.andExpect(jsonPath("$.precios.kitCompleto").value(300000))
				.andExpect(jsonPath("$.precios.acompanante").value(150000))
				.andExpect(jsonPath("$.precios.acompananteConKit").value(350000))
				.andExpect(jsonPath("$.precios.cuatrimoto").value(150000))
				.andExpect(jsonPath("$.precios.parapente").value(200000))
				.andExpect(jsonPath("$.precios.hotelNoche").value(45000))
				.andExpect(jsonPath("$.precios.moneda").value("COP"));
	}

	@Test
	void configVersionTerminos() throws Exception {
		mvc.perform(get("/api/v1/config")).andExpect(jsonPath("$.versionTerminos").value("TYC-2026-10-28"));
	}

	@Test
	void disponibilidadSinReservas() throws Exception {
		LocalDate hoy = LocalDate.now(ZoneId.of("America/Bogota"));
		disponibilidad()
				.andExpect(jsonPath("$.capacity").value(60))
				.andExpect(jsonPath("$.occupied").value(0))
				.andExpect(jsonPath("$.available").value(60))
				.andExpect(jsonPath("$.paramo.capacity").value(60))
				.andExpect(jsonPath("$.paramo.occupied").value(0))
				.andExpect(jsonPath("$.paramo.available").value(60))
				.andExpect(jsonPath("$.fechaCierre").value("2026-11-09"))
				// tolerancia por si la prueba cruza la medianoche de Bogota
				.andExpect(jsonPath("$.today").value(anyOf(is(hoy.toString()), is(hoy.plusDays(1).toString()))));
	}

	@Test
	void unaReservaConPagoPorRevisarYaApartaSuCupoYSusCamas() throws Exception {
		reserva("Páramo", null);
		disponibilidad().andExpect(jsonPath("$.occupied").value(1)).andExpect(jsonPath("$.available").value(59))
				.andExpect(jsonPath("$.paramo.occupied").value(1)).andExpect(jsonPath("$.paramo.available").value(59));
	}

	@Test
	void cadaReservaGastaUnCupoYCadaPersonaEnParamoUnaCama() throws Exception {
		reserva("Páramo", null);
		disponibilidad().andExpect(jsonPath("$.occupied").value(1)).andExpect(jsonPath("$.paramo.occupied").value(1));

		reserva("Páramo", "Páramo");
		disponibilidad().andExpect(jsonPath("$.occupied").value(2)).andExpect(jsonPath("$.available").value(58))
				.andExpect(jsonPath("$.paramo.occupied").value(3)).andExpect(jsonPath("$.paramo.available").value(57));

		aprobar(reserva("Páramo", "Hotel en Manaure"));
		disponibilidad().andExpect(jsonPath("$.occupied").value(3)).andExpect(jsonPath("$.paramo.occupied").value(4));
	}

	@Test
	void hotelYRegresoACasaGastanCupoDeCarreraPeroNoCamas() throws Exception {
		reserva("Hotel en Manaure", null);
		aprobar(reserva("Regreso a casa", "Regreso a casa"));
		disponibilidad().andExpect(jsonPath("$.occupied").value(2)).andExpect(jsonPath("$.available").value(58))
				.andExpect(jsonPath("$.paramo.occupied").value(0)).andExpect(jsonPath("$.paramo.available").value(60));
	}

	@Test
	void unaReservaInactivaLiberaSuCupoYSusCamas() throws Exception {
		Pago pago = reserva("Páramo", "Páramo");
		jdbc.update("update inscripcion set activa = false where id = ?", pago.getInscripcion().getId());
		disponibilidad().andExpect(jsonPath("$.occupied").value(0)).andExpect(jsonPath("$.available").value(60))
				.andExpect(jsonPath("$.paramo.occupied").value(0)).andExpect(jsonPath("$.paramo.available").value(60));
	}

	@Test
	void availableNuncaEsNegativo() throws Exception {
		reserva("Páramo", "Páramo");
		reserva("Hotel en Manaure", null);
		jdbc.update("update configuracion_evento set cupos_carrera = 1, cupos_paramo = 1");
		disponibilidad().andExpect(jsonPath("$.capacity").value(1)).andExpect(jsonPath("$.occupied").value(2))
				.andExpect(jsonPath("$.available").value(0)).andExpect(jsonPath("$.paramo.capacity").value(1))
				.andExpect(jsonPath("$.paramo.occupied").value(2)).andExpect(jsonPath("$.paramo.available").value(0));
	}

	@Test
	void ambasRutasSonPublicasYSinDatosPersonales() throws Exception {
		aprobar(reserva("Páramo", "Páramo"));
		String config = mvc.perform(get("/api/v1/config")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json")).andReturn().getResponse()
				.getContentAsString();
		String disp = mvc.perform(get("/api/v1/disponibilidad")).andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json")).andReturn().getResponse()
				.getContentAsString();
		for (String cuerpo : new String[] { config, disp }) {
			assertThat(cuerpo).doesNotContain("ANA", "PRUEBA", "7000", "ana@example.com", "PER-", "PAG-", "admin",
					"correo", "documento", "actualizadoPor");
		}
	}

}
