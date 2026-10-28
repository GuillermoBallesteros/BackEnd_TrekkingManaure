package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** HU-N04, criterios de error: 400, 409 y 422 con su {@code code} y {@code campo}. */
class ReservaPublicaErroresTest extends ReservaPublicaBase {

	private static final String[] TABLAS = { "pago", "inscripcion_extra", "hotel_previo", "inscripcion_participante",
			"consentimiento_persona", "contacto_emergencia", "ficha_salud", "inscripcion", "persona" };

	// --- 400: estructura (diseño §8, paso 1)

	@Test
	void jsonMalFormadoDa400() throws Exception {
		enviar("{\"firstName\": \"Ana\", ").andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "phone", "eps", "emergencyContact", "health", "commitments", "consent", "extras", "plan",
			"emergencyContact.phone", "companion.phone", "companion.kit" })
	void objetoObligatorioAusenteDa400ConElCampo(String ruta) throws Exception {
		enviar(ReservaJson.conAcompanante().quitar(ruta)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.campo").value(ruta));
	}

	static Stream<Arguments> tiposIncorrectos() {
		return Stream.of(
				Arguments.of("extras", "cuatrimoto"),
				Arguments.of("health.exercises", "true"),
				Arguments.of("firstName", 123),
				Arguments.of("phone", "3001234567"),
				Arguments.of("consent.imageUse", 1),
				Arguments.of("companion.kit", "false"),
				Arguments.of("plan", true),
				Arguments.of("extras", Arrays.asList((Object) null)));
	}

	@ParameterizedTest
	@MethodSource("tiposIncorrectos")
	void tipoIncorrectoDa400(String ruta, Object valor) throws Exception {
		enviar(ReservaJson.conAcompanante().set(ruta, valor)).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
		assertThat(contar("inscripcion")).isZero();
	}

	// --- 409: cierre

	@Test
	void despuesDelCierreDa409ConLaFecha() throws Exception {
		reloj.fijar(Instant.parse("2026-11-10T15:00:00Z"));
		enviar(ReservaJson.titular()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INSCRIPCIONES_CERRADAS"))
				.andExpect(jsonPath("$.detail").value("Las inscripciones cerraron el 9 de noviembre."));
		assertThat(contar("inscripcion")).isZero();
	}

	@Test
	void elDiaDelCierreTodaviaSeAcepta() throws Exception {
		reloj.fijar(Instant.parse("2026-11-09T22:00:00Z"));
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
	}

	// --- 422: un caso por código del diseño §12.1, para titular y para acompañante

	/** code, campo (sin prefijo) y las modificaciones (ruta, valor) que provocan el error en esa persona. */
	private record CasoPersona(String code, String campo, Object... cambios) {
	}

	private static final List<CasoPersona> CASOS_PERSONA = List.of(
			new CasoPersona("NOMBRE_INVALIDO", "firstName", "firstName", "A1"),
			new CasoPersona("FECHA_NACIMIENTO_INVALIDA", "birthDate", "birthDate", "1990-02-31"),
			new CasoPersona("TIPO_DOCUMENTO_INVALIDO", "documentType", "documentType", "XX"),
			new CasoPersona("DOCUMENTO_INVALIDO", "document", "document", "12"),
			new CasoPersona("DOCUMENTO_EDAD_INCOMPATIBLE", "documentType", "birthDate", "2015-01-01"),
			new CasoPersona("GENERO_INVALIDO", "gender", "gender", "Marciano"),
			new CasoPersona("TELEFONO_INVALIDO", "phone.number", "phone.number", "123"),
			new CasoPersona("CORREO_INVALIDO", "email", "email", "sin-arroba"),
			new CasoPersona("CIUDAD_INVALIDA", "city", "city", "123"),
			new CasoPersona("EPS_INVALIDA", "eps.entity", "eps.entity", "Inventada"),
			new CasoPersona("CONTACTO_EMERGENCIA_INVALIDO", "emergencyContact.name", "emergencyContact.name", "Luis"),
			new CasoPersona("SALUD_INVALIDA", "health.illness", "health.illness", "x"),
			new CasoPersona("COMPROMISO_REQUERIDO", "commitments.protectedAreas", "commitments.protectedAreas", false),
			new CasoPersona("CONSENTIMIENTO_REQUERIDO", "consent.acceptedAt", "consent.acceptedAt", ""),
			new CasoPersona("TERMINOS_DESACTUALIZADOS", "consent.version", "consent.version", "TYC-VIEJO"),
			new CasoPersona("USO_IMAGEN_INVALIDO", "consent.imageUse", "consent.imageUse", null),
			new CasoPersona("ESTADIA_INVALIDA", "stay", "stay", "Luna"));

	private static void aplicar(ReservaJson json, String prefijo, Object[] cambios) {
		for (int i = 0; i < cambios.length; i += 2) {
			json.set(prefijo + cambios[i], cambios[i + 1]);
		}
	}

	private static Arguments caso(String code, String campo, Consumer<ReservaJson> cambio) {
		return Arguments.of(code, campo, cambio);
	}

	static Stream<Arguments> casos422() {
		List<Arguments> casos = new ArrayList<>();
		for (CasoPersona c : CASOS_PERSONA) {
			casos.add(caso(c.code(), c.campo(), j -> aplicar(j, "", c.cambios())));
			casos.add(caso(c.code(), "companion." + c.campo(), j -> aplicar(j, "companion.", c.cambios())));
		}
		// Talla: titular con talla fuera del catálogo; acompañante con kit sin talla y sin kit con talla.
		casos.add(caso("TALLA_INVALIDA", "shirtSize", j -> j.set("shirtSize", "Z")));
		casos.add(caso("TALLA_INVALIDA", "companion.shirtSize", j -> j.set("companion.kit", true)));
		casos.add(caso("TALLA_INVALIDA", "companion.shirtSize", j -> j.set("companion.shirtSize", "M")));
		// Reglas de la reserva o del titular.
		casos.add(caso("MENOR_SIN_RESPONSABLE", "emergencyContact.relationship", j -> j.set("documentType", "TI")
				.set("document", "1234567890").set("birthDate", "2012-03-01")));
		casos.add(caso("EXTRA_INVALIDO", "extras", j -> j.set("extras", List.of("volar"))));
		casos.add(caso("EXTRA_INVALIDO", "extras", j -> j.set("extras", List.of("parapente", "parapente"))));
		casos.add(caso("EXTRA_INVALIDO", "extras", j -> j.set("companion", null).set("extras",
				List.of("parapenteAcompanante"))));
		casos.add(caso("HOTEL_INVALIDO", "preEventHotel.checkIn", j -> j.set("preEventHotel",
				Map.of("checkIn", "2026-10-20", "checkOut", "2026-10-22", "withCompanion", false))));
		casos.add(caso("ACOMPANANTE_REPETIDO", "companion.document", j -> j.set("companion.document",
				"1065123456")));
		casos.add(caso("PLAN_INVALIDO", "plan", j -> j.set("plan", "mensual")));
		casos.add(caso("FECHA_COMPROMISO_INVALIDA", "commitmentDate", j -> j.set("plan", "apartado")));
		return casos.stream();
	}

	@ParameterizedTest(name = "{0} en {1}")
	@MethodSource("casos422")
	void reglaDeNegocioIncumplidaDa422(String code, String campo, Consumer<ReservaJson> cambio) throws Exception {
		ReservaJson json = ReservaJson.conAcompanante();
		cambio.accept(json);
		enviar(json).andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.campo").value(campo))
				.andExpect(jsonPath("$.detail").isNotEmpty());
		sinFilas();
	}

	@Test
	void campoDelTelefonoDelAcompananteEsElDeLaHistoria() throws Exception {
		enviar(ReservaJson.conAcompanante().set("companion.phone.number", "123"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.code").value("TELEFONO_INVALIDO"))
				.andExpect(jsonPath("$.campo").value("companion.phone.number"))
				.andExpect(jsonPath("$.detail").value(
						"El celular del acompañante no es válido para Colombia (ejemplo: 300 123 4567)."));
	}

	// --- 409: documento duplicado

	@Test
	void documentoDelTitularYaReservadoDa409() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		enviar(ReservaJson.titular().set("email", "otra@example.com")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"))
				.andExpect(jsonPath("$.campo").value("document"))
				.andExpect(jsonPath("$.detail").value("El documento 1065123456 ya tiene una reserva registrada. Si no fuiste tú, comunícate con la organización para que la revisen."));
		assertThat(contar("inscripcion")).isEqualTo(1);
	}

	@Test
	void documentoDelAcompananteYaReservadoDa409() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		enviar(ReservaJson.conAcompanante().set("document", "1065000111").set("companion.document", "1065123456"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"))
				.andExpect(jsonPath("$.campo").value("companion.document"));
		assertThat(contar("inscripcion")).isEqualTo(1);
	}

	@Test
	void documentoEnMinusculasChocaConElDeMayusculas() throws Exception {
		enviar(ReservaJson.titular().set("documentType", "Pasaporte").set("document", "AB12345"))
				.andExpect(status().isCreated());
		enviar(ReservaJson.titular().set("documentType", "Pasaporte").set("document", "ab12345"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"));
	}

	@Test
	void documentoSoloEnUnaInscripcionRechazadaSeAcepta() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		rechazar("PER-0001");

		enviar(ReservaJson.titular()).andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value("PER-0002"));
	}

	@Test
	void documentoEnInscripcionConSaldoOPagadaSigueActivo() throws Exception {
		enviar(ReservaJson.titular()).andExpect(status().isCreated());
		aprobarPagos("PER-0001");
		enviar(ReservaJson.titular()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"));
	}

	/** Rechaza por el endpoint real (HU-N08): inscripción inactiva y documento liberado. */
	private void rechazar(String codigo) throws Exception {
		for (String pago : jdbc.queryForList(
				"select codigo from pago where inscripcion_id = (select id from inscripcion where codigo = ?)",
				String.class, codigo)) {
			revisar(pago, "Rechazado", "Comprobante ilegible").andExpect(status().isOk());
		}
	}

	// --- 409: cupo del páramo

	@Test
	void sinCuposLibresDa409ConElMensajeDeAgotados() throws Exception {
		enviar(ReservaJson.titular().set("document", "1065000111")).andExpect(status().isCreated());
		aprobarPagos("PER-0001");
		jdbc.update("update configuracion_evento set cupos_paramo = 1");

		enviar(ReservaJson.titular()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SIN_CUPO_PARAMO"))
				.andExpect(jsonPath("$.campo").value("stay"))
				.andExpect(jsonPath("$.detail").value(
						"Los cupos del páramo están agotados: elige hotel en Manaure o regreso a casa."));
		assertThat(contar("inscripcion")).isEqualTo(1);
	}

	@Test
	void unCupoLibreYDosPersonasEnParamoDa409ConElMensajeDeUnCupo() throws Exception {
		enviar(ReservaJson.titular().set("document", "1065000111")).andExpect(status().isCreated());
		aprobarPagos("PER-0001");
		jdbc.update("update configuracion_evento set cupos_paramo = 2");

		enviar(ReservaJson.conAcompanante()).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SIN_CUPO_PARAMO"))
				.andExpect(jsonPath("$.detail").value("Solo queda 1 cupo en el páramo y tu reserva necesita 2. "
						+ "Elige otra estadía para alguno de los dos."));
		// Con una de las dos personas fuera del páramo sí cabe.
		enviar(ReservaJson.conAcompanante().set("companion.stay", "Hotel en Manaure"))
				.andExpect(status().isCreated());
	}

	// --- atomicidad

	private void sinFilas() {
		for (String tabla : TABLAS) {
			assertThat(contar(tabla)).as(tabla).isZero();
		}
	}

	@Test
	void unaValidacionFallidaNoDejaRegistrosParciales() throws Exception {
		enviar(ReservaJson.conAcompanante().set("companion.phone.number", "123"))
				.andExpect(status().isUnprocessableEntity());
		sinFilas();
	}

	@Test
	void unFalloALaMitadDeLaEscrituraRevierteTodoYSeTraduceA409() throws Exception {
		// La inscripción 1 quedó rechazada pero conserva documento_activo (estado inconsistente a propósito):
		// las reglas dejan pasar el documento y el índice único lo rechaza al guardar el acompañante, cuando el
		// titular, la inscripción y el pago ya se habían escrito en la misma transacción.
		enviar(ReservaJson.conAcompanante()).andExpect(status().isCreated());
		jdbc.update("update pago set revision = 'RECHAZADO', motivo_rechazo = 'x', revisado_en = now()");
		int personas = contar("persona");
		int inscripciones = contar("inscripcion");
		int pagos = contar("pago");

		enviar(ReservaJson.conAcompanante().set("document", "1065000111").set("email", "nuevo@example.com")
				.set("phone.number", "3002223344").set("companion.document", "1065999888"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENTO_DUPLICADO"));

		assertThat(contar("persona")).isEqualTo(personas);
		assertThat(contar("inscripcion")).isEqualTo(inscripciones);
		assertThat(contar("pago")).isEqualTo(pagos);
		assertThat(contar("inscripcion_participante")).isEqualTo(2);
		assertThat(contar("ficha_salud")).isEqualTo(2);
		assertThat(contar("consentimiento_persona")).isEqualTo(2);
	}

}
