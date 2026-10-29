package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** HU-N04, criterios de éxito: contra PostgreSQL 18 real. */
class ReservaPublicaCreacionTest extends ReservaPublicaBase {

	private static final String ACEPTADO_SERVIDOR = "2026-10-28T15:20:11.482Z";

	@Test
	void titularSoloPlanTotal() throws Exception {
		enviar(ReservaJson.titular())
				.andExpect(status().isCreated())
				.andExpect(header().doesNotExist("Location"))
				.andExpect(jsonPath("$.id").value("PER-0001"))
				.andExpect(jsonPath("$.createdAt").value("2026-10-28"))
				.andExpect(jsonPath("$.guardianAuthorized").value(false))
				.andExpect(jsonPath("$.payments.length()").value(1))
				.andExpect(jsonPath("$.payments[0].id").value("PAG-0001"))
				.andExpect(jsonPath("$.payments[0].concept").value("Pago total"))
				.andExpect(jsonPath("$.payments[0].amount").value(350000))
				.andExpect(jsonPath("$.payments[0].date").value("2026-10-28"))
				.andExpect(jsonPath("$.payments[0].review").value("Por revisar"))
				.andExpect(jsonPath("$.payments[0].rejectionReason").value(nullValue()))
				.andExpect(jsonPath("$.total").value(350000));
		assertThat(contar("inscripcion")).isEqualTo(1);
		assertThat(contar("pago")).isEqualTo(1);
		assertThat(jdbc.queryForObject("select documento_activo from inscripcion_participante", String.class))
				.isEqualTo("1065123456");
	}

	@Test
	void titularConAcompananteSinKitCuatrimotoYApartado() throws Exception {
		enviar(ReservaJson.conAcompanante().set("extras", List.of("cuatrimoto")).set("plan", "apartado")
				.set("commitmentDate", "2026-11-09"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.payments.length()").value(1))
				.andExpect(jsonPath("$.payments[0].concept").value("Separación de cupo"))
				.andExpect(jsonPath("$.payments[0].amount").value(50000))
				.andExpect(jsonPath("$.payments[0].review").value("Por revisar"))
				.andExpect(jsonPath("$.plan").value("apartado"))
				.andExpect(jsonPath("$.commitmentDate").value("2026-11-09"))
				.andExpect(jsonPath("$.extras[0]").value("cuatrimoto"))
				.andExpect(jsonPath("$.companion.kit").value(false))
				.andExpect(jsonPath("$.companion.shirtSize").value(nullValue()))
				.andExpect(jsonPath("$.total").value(650000));
		assertThat(jdbc.queryForObject("select fecha_compromiso::text from inscripcion", String.class))
				.isEqualTo("2026-11-09");
		assertThat(contar("inscripcion_participante")).isEqualTo(2);
	}

	@Test
	void hotelPreviosDosNochesConAcompananteSuma180000() throws Exception {
		enviar(ReservaJson.conAcompanante().set("preEventHotel",
				Map.of("checkIn", "2026-11-12", "checkOut", "2026-11-14", "withCompanion", true)))
				.andExpect(status().isCreated())
				// 350000 titular + 150000 acompañante + 2 noches x 2 personas x 45000
				.andExpect(jsonPath("$.total").value(350000 + 150000 + 180000))
				.andExpect(jsonPath("$.preEventHotel.checkIn").value("2026-11-12"))
				.andExpect(jsonPath("$.preEventHotel.checkOut").value("2026-11-14"))
				.andExpect(jsonPath("$.preEventHotel.withCompanion").value(true))
				.andExpect(jsonPath("$.payments[0].amount").value(680000));
		assertThat(contar("hotel_previo")).isEqualTo(1);
	}

	@Test
	void totalYEstadoDelClienteSeIgnoran() throws Exception {
		enviar(ReservaJson.titular().set("total", 1).set("id", "PER-9999").set("createdAt", "2000-01-01")
				.set("guardianAuthorized", true).set("status", "Pagado")
				.set("payments", List.of(Map.of("id", "PAG-9999", "concept", "Pago total", "amount", 1,
						"date", "2000-01-01", "review", "Aprobado", "rejectionReason", "x"))))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("PER-0001"))
				.andExpect(jsonPath("$.createdAt").value("2026-10-28"))
				.andExpect(jsonPath("$.total").value(350000))
				.andExpect(jsonPath("$.guardianAuthorized").value(false))
				.andExpect(jsonPath("$.payments.length()").value(1))
				.andExpect(jsonPath("$.payments[0].id").value("PAG-0001"))
				.andExpect(jsonPath("$.payments[0].amount").value(350000))
				.andExpect(jsonPath("$.payments[0].review").value("Por revisar"));
		assertThat(jdbc.queryForObject("select count(*) from pago where revision = 'POR_REVISAR'", Integer.class))
				.isEqualTo(1);
	}

	@Test
	void aceptacionLaFijaElServidorParaTitularYAcompanante() throws Exception {
		enviar(ReservaJson.conAcompanante())
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.consent.acceptedAt").value(ACEPTADO_SERVIDOR))
				.andExpect(jsonPath("$.companion.consent.acceptedAt").value(ACEPTADO_SERVIDOR));
		assertThat(jdbc.queryForObject("select count(*) from consentimiento_persona "
				+ "where aceptado_en = '2026-10-28T15:20:11.482Z'::timestamptz", Integer.class)).isEqualTo(2);
	}

	@Test
	void preciosAplicadosNoCambianConUnCambioPosterior() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(350000));
		jdbc.update("update configuracion_evento set precio_titular = 400000");

		assertThat(jdbc.queryForObject("select total from inscripcion where codigo = 'PER-0001'", Long.class))
				.isEqualTo(350000L);
		assertThat(jdbc.queryForObject("select precio_titular_aplicado from inscripcion where codigo = 'PER-0001'",
				Long.class)).isEqualTo(350000L);
		assertThat(jdbc.queryForObject("select precio_hotel_noche_aplicado from inscripcion", Long.class))
				.isEqualTo(45000L);
		enviar(ReservaJson.titular().set("document", "1065000111")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.total").value(400000));
	}

	@Test
	void codigosSalenDeLaSecuencia() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("PER-0001"))
				.andExpect(jsonPath("$.payments[0].id").value("PAG-0001"));
		enviar(ReservaJson.titular().set("document", "1065000111")).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("PER-0002"))
				.andExpect(jsonPath("$.payments[0].id").value("PAG-0002"));
	}

	@Test
	void respuestaConLaFormaDelTipoRegistration() throws Exception {
		enviar(ReservaJson.titular().set("eps", Map.of("entity", "Otra", "other", "Salud Vida")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.firstName").value("ANA MARIA"))
				.andExpect(jsonPath("$.lastName").value("PEREZ GOMEZ"))
				.andExpect(jsonPath("$.documentType").value("CC"))
				.andExpect(jsonPath("$.document").value("1065123456"))
				.andExpect(jsonPath("$.birthDate").value("1990-05-14"))
				.andExpect(jsonPath("$.gender").value("Femenino"))
				.andExpect(jsonPath("$.phone.country").value("CO"))
				.andExpect(jsonPath("$.phone.number").value("3001234567"))
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.city").value("Valledupar"))
				.andExpect(jsonPath("$.eps.entity").value("Otra"))
				.andExpect(jsonPath("$.eps.other").value("Salud Vida"))
				.andExpect(jsonPath("$.emergencyContact.name").value("LUIS PEREZ"))
				.andExpect(jsonPath("$.emergencyContact.phone.country").value("CO"))
				.andExpect(jsonPath("$.emergencyContact.phone.number").value("3157654321"))
				.andExpect(jsonPath("$.emergencyContact.relationship").value("Hermano o hermana"))
				.andExpect(jsonPath("$.health.illness").value(nullValue()))
				.andExpect(jsonPath("$.health.allergies").value("Penicilina"))
				.andExpect(jsonPath("$.health.disability").value(nullValue()))
				.andExpect(jsonPath("$.health.exercises").value(true))
				.andExpect(jsonPath("$.commitments.protectedAreas").value(true))
				.andExpect(jsonPath("$.commitments.environment").value(true))
				.andExpect(jsonPath("$.consent.version").value("TYC-2026-10-28"))
				.andExpect(jsonPath("$.consent.acceptedAt").value(ACEPTADO_SERVIDOR))
				.andExpect(jsonPath("$.consent.imageUse").value(true))
				.andExpect(jsonPath("$.stay").value("Páramo"))
				.andExpect(jsonPath("$.shirtSize").value("M"))
				.andExpect(jsonPath("$.companion").value(nullValue()))
				.andExpect(jsonPath("$.extras.length()").value(0))
				.andExpect(jsonPath("$.preEventHotel").value(nullValue()))
				.andExpect(jsonPath("$.plan").value("total"))
				.andExpect(jsonPath("$.commitmentDate").value(nullValue()));
	}

	@Test
	void respuestaDelAcompananteConSaludYKit() throws Exception {
		enviar(ReservaJson.conAcompanante().set("companion.kit", true).set("companion.shirtSize", "L"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.companion.firstName").value("CARLOS"))
				.andExpect(jsonPath("$.companion.health.illness").value("Asma leve"))
				.andExpect(jsonPath("$.companion.health.exercises").value(false))
				.andExpect(jsonPath("$.companion.kit").value(true))
				.andExpect(jsonPath("$.companion.shirtSize").value("L"))
				.andExpect(jsonPath("$.companion.emergencyContact.relationship").value("Madre"))
				.andExpect(jsonPath("$.companion.consent.imageUse").value(false))
				.andExpect(jsonPath("$.total").value(700000));
	}

	@Test
	void correoSeGuardaEnMinusculasYDocumentoEnMayusculas() throws Exception {
		enviar(ReservaJson.titular().set("email", "ANA@Example.com").set("documentType", "Pasaporte")
				.set("document", "ab12345"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("ana@example.com"))
				.andExpect(jsonPath("$.document").value("AB12345"));
	}

	@Test
	void titularMenorConContactoMadreSeAceptaSinAutorizacion() throws Exception {
		enviar(ReservaJson.titular().set("documentType", "TI").set("document", "1234567890")
				.set("birthDate", "2012-03-01").set("emergencyContact.relationship", "Madre"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.guardianAuthorized").value(false));
		assertThat(jdbc.queryForObject("select autorizacion_menor from inscripcion", Boolean.class)).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Hotel en Manaure", "Regreso a casa" })
	void estadiaSinCupoSeAceptaConElParamoAgotado(String estadia) throws Exception {
		enviar(ReservaJson.titular().set("document", "1065000111")).andExpect(status().isCreated());
		aprobarPagos("PER-0001");
		jdbc.update("update configuracion_evento set cupos_paramo = 1");

		enviar(ReservaJson.conAcompanante().set("stay", estadia).set("companion.stay", estadia))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.stay").value(estadia))
				.andExpect(jsonPath("$.companion.stay").value(estadia));
	}

	@Test
	void fichaDeSaludQuedaCifradaEnLaBase() throws Exception {
		enviar(ReservaJson.conAcompanante().set("health.illness", "Diabetes tipo dos"))
				.andExpect(status().isCreated());

		Map<String, Object> fila = jdbc.queryForMap("""
				select f.tiene_enfermedad, f.tiene_alergias, f.tiene_discapacidad, f.enfermedad_cifrada,
				       f.alergias_cifrada, f.discapacidad_cifrada
				from ficha_salud f join persona p on p.id = f.persona_id where p.documento = '1065123456'""");
		assertThat(fila.get("tiene_enfermedad")).isEqualTo(true);
		assertThat(fila.get("tiene_alergias")).isEqualTo(true);
		assertThat(fila.get("tiene_discapacidad")).isEqualTo(false);
		assertThat(fila.get("discapacidad_cifrada")).isNull();
		for (String columna : new String[] { "enfermedad_cifrada", "alergias_cifrada" }) {
			assertThat((String) fila.get(columna)).startsWith("1:").doesNotContain("Diabetes", "Penicilina");
		}
		String asma = jdbc.queryForObject("""
				select f.enfermedad_cifrada from ficha_salud f join persona p on p.id = f.persona_id
				where p.documento = '1065999888'""", String.class);
		assertThat(asma).startsWith("1:").doesNotContain("Asma");
	}

}
