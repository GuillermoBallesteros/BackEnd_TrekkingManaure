package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManagerFactory;

/** HU-N07 con PostgreSQL 18 real: lista de reservas del panel. */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ReservaAdminListaTest extends ReservaPublicaBase {

	private static final String RUTA = "/api/v1/admin/reservas";

	@Autowired
	EntityManagerFactory emf;

	private ResultActions listar() throws Exception {
		String token = jwt.emitir(1L, "admin@ejemplo.com", Instant.now()).token();
		return mvc.perform(get(RUTA).header("Authorization", "Bearer " + token));
	}

	private void crear(int n) throws Exception {
		crear(ReservaJson.conAcompanante().set("document", "1065000" + n).set("companion.document", "1065900" + n));
	}

	private void crear(ReservaJson r) throws Exception {
		enviar(r).andExpect(status().isCreated());
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(get(RUTA)).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void devuelveTodasLasReservasIncluidasLasRechazadas() throws Exception {
		crear(1);
		crear(2);
		revisar("PAG-0001", "Rechazado", "No aparece en Bre-B").andExpect(status().isOk());
		listar().andExpect(status().isOk()).andExpect(jsonPath("$.registrations", hasSize(2)))
				.andExpect(jsonPath("$.registrations[1].id").value("PER-0001"))
				.andExpect(jsonPath("$.registrations[1].payments[0].review").value("Rechazado"))
				.andExpect(jsonPath("$.registrations[1].payments[0].rejectionReason").value("No aparece en Bre-B"))
				.andExpect(jsonPath("$.registrations[0].payments[0].review").value("Por revisar"))
				.andExpect(jsonPath("$.registrations[0].payments[0].rejectionReason").value(nullValue()));
	}

	@Test
	void ordenaPorIdDescendente() throws Exception {
		crear(1);
		crear(2);
		listar().andExpect(jsonPath("$.registrations[0].id").value("PER-0002"))
				.andExpect(jsonPath("$.registrations[1].id").value("PER-0001"));
	}

	@Test
	void saludLlegaDescifradaConNullEnDetallesVacios() throws Exception {
		crear(1);
		listar().andExpect(jsonPath("$.registrations[0].health.illness").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].health.allergies").value("Penicilina"))
				.andExpect(jsonPath("$.registrations[0].health.disability").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].health.exercises").value(true))
				.andExpect(jsonPath("$.registrations[0].companion.health.illness").value("Asma leve"))
				.andExpect(jsonPath("$.registrations[0].companion.health.allergies").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].companion.health.exercises").value(false));
		// En la base está cifrada: lo leído no es el texto plano.
		assertThat(jdbc.queryForObject("select alergias_cifrada from ficha_salud where alergias_cifrada is not null limit 1",
				String.class)).doesNotContain("Penicilina");
	}

	@Test
	void capacidadFechaCierreYHoyVienenDeConfiguracionYReloj() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 75");
		String cierre = jdbc.queryForObject("select fecha_cierre::text from configuracion_evento", String.class);
		listar().andExpect(jsonPath("$.registrations", hasSize(0))).andExpect(jsonPath("$.capacity").value(75))
				.andExpect(jsonPath("$.fechaCierre").value(cierre)).andExpect(jsonPath("$.today").value("2026-10-28"));
		// Pasada la medianoche UTC sigue siendo el mismo día en Bogotá.
		reloj.fijar(Instant.parse("2026-10-29T03:30:00Z"));
		listar().andExpect(jsonPath("$.today").value("2026-10-28"));
	}

	@Test
	void formaDeRegistrationCumpleElContratoDelFront() throws Exception {
		crear(ReservaJson.conAcompanante().set("extras", List.of("cuatrimoto")).set("plan", "apartado")
				.set("commitmentDate", "2026-11-09")
				.set("preEventHotel", Map.of("checkIn", "2026-11-12", "checkOut", "2026-11-14", "withCompanion", true)));
		listar().andExpect(status().isOk())
				.andExpect(jsonPath("$.registrations[0].id").value("PER-0001"))
				.andExpect(jsonPath("$.registrations[0].createdAt").value("2026-10-28"))
				.andExpect(jsonPath("$.registrations[0].firstName").value("ANA MARIA"))
				.andExpect(jsonPath("$.registrations[0].lastName").value("PEREZ GOMEZ"))
				.andExpect(jsonPath("$.registrations[0].documentType").value("CC"))
				.andExpect(jsonPath("$.registrations[0].document").isString())
				.andExpect(jsonPath("$.registrations[0].birthDate").value("1990-05-14"))
				.andExpect(jsonPath("$.registrations[0].gender").value("Femenino"))
				.andExpect(jsonPath("$.registrations[0].phone.country").value("CO"))
				.andExpect(jsonPath("$.registrations[0].phone.number").value("3001234567"))
				.andExpect(jsonPath("$.registrations[0].email").value("ana@example.com"))
				.andExpect(jsonPath("$.registrations[0].city").value("Valledupar"))
				.andExpect(jsonPath("$.registrations[0].eps.entity").value("Nueva EPS"))
				.andExpect(jsonPath("$.registrations[0].eps.other").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].emergencyContact.name").value("LUIS PEREZ"))
				.andExpect(jsonPath("$.registrations[0].emergencyContact.phone.number").value("3157654321"))
				.andExpect(jsonPath("$.registrations[0].emergencyContact.relationship").value("Hermano o hermana"))
				.andExpect(jsonPath("$.registrations[0].commitments.protectedAreas").value(true))
				.andExpect(jsonPath("$.registrations[0].commitments.environment").value(true))
				.andExpect(jsonPath("$.registrations[0].consent.version").value("TYC-2026-10-28"))
				.andExpect(jsonPath("$.registrations[0].consent.acceptedAt").value("2026-10-28T15:20:11.482Z"))
				.andExpect(jsonPath("$.registrations[0].consent.imageUse").value(true))
				.andExpect(jsonPath("$.registrations[0].stay").value("Páramo"))
				.andExpect(jsonPath("$.registrations[0].shirtSize").value("M"))
				.andExpect(jsonPath("$.registrations[0].companion.firstName").value("CARLOS"))
				.andExpect(jsonPath("$.registrations[0].companion.kit").value(false))
				.andExpect(jsonPath("$.registrations[0].companion.shirtSize").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].extras[0]").value("cuatrimoto"))
				.andExpect(jsonPath("$.registrations[0].preEventHotel.checkIn").value("2026-11-12"))
				.andExpect(jsonPath("$.registrations[0].preEventHotel.checkOut").value("2026-11-14"))
				.andExpect(jsonPath("$.registrations[0].preEventHotel.withCompanion").value(true))
				.andExpect(jsonPath("$.registrations[0].plan").value("apartado"))
				.andExpect(jsonPath("$.registrations[0].commitmentDate").value("2026-11-09"))
				.andExpect(jsonPath("$.registrations[0].guardianAuthorized").value(false))
				.andExpect(jsonPath("$.registrations[0].payments[0].id").value("PAG-0001"))
				.andExpect(jsonPath("$.registrations[0].payments[0].concept").value("Separación de cupo"))
				.andExpect(jsonPath("$.registrations[0].payments[0].amount").value(50000))
				.andExpect(jsonPath("$.registrations[0].payments[0].date").value("2026-10-28"))
				.andExpect(jsonPath("$.registrations[0].payments[0].review").value("Por revisar"))
				.andExpect(jsonPath("$.registrations[0].payments[0].rejectionReason").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].total").isNumber());
	}

	@Test
	void sinAcompananteNiHotelDaNull() throws Exception {
		crear(ReservaJson.titular());
		listar().andExpect(jsonPath("$.registrations[0].companion").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].preEventHotel").value(nullValue()))
				.andExpect(jsonPath("$.registrations[0].extras", hasSize(0)));
	}

	@Test
	void valoresDeCatalogoViajanConElTextoDelFront() throws Exception {
		crear(ReservaJson.titular().set("stay", "Hotel en Manaure")
				.set("emergencyContact.relationship", "Tutor legal o acudiente"));
		listar().andExpect(jsonPath("$.registrations[0].stay").value("Hotel en Manaure"))
				.andExpect(jsonPath("$.registrations[0].emergencyContact.relationship")
						.value("Tutor legal o acudiente"));
		crear(ReservaJson.titular().set("document", "1065222333").set("email", "otra@example.com"));
		listar().andExpect(jsonPath("$.registrations[0].stay").value("Páramo"));
	}

	@Test
	void noHaceN1ConCincuentaReservas() throws Exception {
		SessionFactory sf = emf.unwrap(SessionFactory.class);
		crear(1);
		crear(2);
		listar().andExpect(status().isOk());
		long antes = sf.getStatistics().getPrepareStatementCount();
		listar().andExpect(jsonPath("$.registrations", hasSize(2)));
		long conDos = sf.getStatistics().getPrepareStatementCount() - antes;

		for (int i = 3; i <= 50; i++) {
			crear(ReservaJson.conAcompanante().set("document", "1065000" + i).set("companion.document", "1065900" + i)
					.set("extras", List.of("cuatrimoto")).set("preEventHotel",
							Map.of("checkIn", "2026-11-12", "checkOut", "2026-11-14", "withCompanion", true)));
		}
		antes = sf.getStatistics().getPrepareStatementCount();
		listar().andExpect(jsonPath("$.registrations", hasSize(50)));
		long conCincuenta = sf.getStatistics().getPrepareStatementCount() - antes;

		assertThat(conCincuenta).isEqualTo(conDos);
		assertThat(conCincuenta).isLessThanOrEqualTo(12);
	}

	@Test
	void llevaCacheControlNoStore() throws Exception {
		listar().andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
	}

	@Test
	void noLogueaDatosDeSaludNiPersonalesNiLosAudita(CapturedOutput salida) throws Exception {
		crear(1);
		listar().andExpect(status().isOk());
		assertThat(salida.getAll()).doesNotContain("Penicilina", "Asma leve", "Ana María", "Carlos", "Marta",
				"ana@example.com", "3001234567", "3157654321");
		assertThat(jdbc.queryForObject("select count(*) from auditoria where detalle like '%Penicilina%'",
				Integer.class)).isZero();
	}

}
