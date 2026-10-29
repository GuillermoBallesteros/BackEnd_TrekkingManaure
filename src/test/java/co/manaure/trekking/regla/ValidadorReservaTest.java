package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.modelo.AcompananteRegla;
import co.manaure.trekking.regla.modelo.HotelSolicitado;
import co.manaure.trekking.regla.modelo.NuevaReserva;
import co.manaure.trekking.regla.modelo.PersonaRegla;

class ValidadorReservaTest {

	private static NuevaReserva base() {
		return Datos.reserva();
	}

	private static NuevaReserva conAcompanante(AcompananteRegla a) {
		return Datos.conAcompanante(base(), a);
	}

	private static NuevaReserva conAcompanante(PersonaRegla titular, PersonaRegla acompanante) {
		return Datos.conAcompanante(Datos.conTitular(base(), titular), new AcompananteRegla(acompanante, false, null));
	}

	private static NuevaReserva reserva(List<String> extras, HotelSolicitado hotel, String plan, String compromiso,
			AcompananteRegla acompanante) {
		return new NuevaReserva(Datos.titular(), "M", acompanante, extras, hotel, plan, compromiso);
	}

	private static Optional<ErrorRegla> validar(NuevaReserva r) {
		return ValidadorReserva.validar(r, Datos.contexto());
	}

	private static ErrorRegla error(NuevaReserva r) {
		return validar(r).orElseThrow();
	}

	private static void assertError(NuevaReserva r, CodigoError code, String detail, String campo) {
		ErrorRegla e = error(r);
		assertThat(e.code()).isEqualTo(code);
		assertThat(e.detail()).isEqualTo(detail);
		assertThat(e.campo()).isEqualTo(campo);
	}

	private static ContextoReserva ctx(String hoy, Set<String> documentos, int cupos) {
		return new ContextoReserva(Datos.CATALOGOS, Datos.EVENTO, LocalDate.parse(hoy), documentos, cupos);
	}

	@Test
	void reservaValidaNoDaError() {
		assertThat(validar(base())).isEmpty();
		assertThat(validar(conAcompanante(Datos.acompanante()))).isEmpty();
	}

	// --- cierre

	@Test
	void despuesDelCierreNoSeInscribe() {
		var e = ValidadorReserva.validar(base(), ctx("2026-11-10", Set.of(), 60)).orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.INSCRIPCIONES_CERRADAS);
		assertThat(e.detail()).isEqualTo("Las inscripciones cerraron el 9 de noviembre.");
	}

	@Test
	void elDiaDelCierreTodaviaSeInscribe() {
		assertThat(ValidadorReserva.validar(base(), ctx("2026-11-09", Set.of(), 60))).isEmpty();
	}

	// --- menor titular

	private static PersonaRegla menor(String parentesco) {
		return PB.titular().tipoDocumento("TI").documento("1234567890").nacimiento("2009-10-29")
				.contacto("LUIS PEREZ", "CO", "3157654321", parentesco).build();
	}

	@Test
	void menorDe17ConAmigoNoTieneResponsable() {
		assertError(Datos.conTitular(base(), menor("Amigo o amiga")), CodigoError.MENOR_SIN_RESPONSABLE,
				"El participante es menor de edad: su contacto debe ser su madre, padre o tutor legal.",
				"emergencyContact.relationship");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Madre", "Padre", "Tutor legal o acudiente" })
	void menorConResponsableLegalPasa(String parentesco) {
		assertThat(validar(Datos.conTitular(base(), menor(parentesco)))).isEmpty();
	}

	@Test
	void mayorDeEdadConAmigoPasa() {
		assertThat(validar(Datos.conTitular(base(),
				PB.titular().contacto("LUIS PEREZ", "CO", "3157654321", "Amigo o amiga").build()))).isEmpty();
	}

	@Test
	void unAcompananteMenorSeAceptaConLasReglasGenerales() {
		var menorAcompanante = PB.acompanante().tipoDocumento("TI").documento("1098765432").nacimiento("2009-10-29")
				.contacto("MARTA ROJAS", "CO", "3201112233", "Amigo o amiga").build();
		assertThat(validar(conAcompanante(Datos.titular(), menorAcompanante))).isEmpty();
	}

	// --- talla del titular

	@ParameterizedTest
	@ValueSource(strings = { "Z", "", "m" })
	void tallaDelTitularFueraDeCatalogo(String talla) {
		var r = new NuevaReserva(Datos.titular(), talla, null, List.of(), null, "total", null);
		assertError(r, CodigoError.TALLA_INVALIDA, "Elige tu talla de camisa.", "shirtSize");
	}

	// --- extras

	@Test
	void extraDesconocido() {
		assertError(reserva(List.of("tirolesa"), null, "total", null, null), CodigoError.EXTRA_INVALIDO,
				"Hay una actividad extra que no existe.", "extras");
	}

	@Test
	void extraRepetido() {
		assertError(reserva(List.of("cuatrimoto", "cuatrimoto"), null, "total", null, null),
				CodigoError.EXTRA_INVALIDO, "Cada actividad extra se elige una sola vez.", "extras");
	}

	@Test
	void parapenteDelAcompananteSinAcompanante() {
		assertError(reserva(List.of("parapenteAcompanante"), null, "total", null, null), CodigoError.EXTRA_INVALIDO,
				"El parapente del acompañante solo se elige si llevas acompañante.", "extras");
	}

	@Test
	void extrasValidosConAcompanante() {
		assertThat(validar(reserva(List.of("cuatrimoto", "parapente", "parapenteAcompanante"), null, "total", null,
				Datos.acompanante()))).isEmpty();
	}

	// --- hotel

	private static NuevaReserva conHotel(String llegada, String salida, boolean conAcompanante,
			AcompananteRegla acompanante) {
		return reserva(List.of(), new HotelSolicitado(llegada, salida, conAcompanante), "total", null, acompanante);
	}

	@Test
	void hotelConLlegadaEl15DeNoviembreUsaElMensajeExacto() {
		assertError(conHotel("2026-11-15", "2026-11-16", false, null), CodigoError.HOTEL_INVALIDO,
				"La llegada al hotel debe estar entre el 28 de octubre y el 14 de noviembre.",
				"preEventHotel.checkIn");
	}

	@Test
	void hotelConLlegadaAntesDeLaVentana() {
		assertThat(error(conHotel("2026-10-27", "2026-10-29", false, null)).detail())
				.isEqualTo("La llegada al hotel debe estar entre el 28 de octubre y el 14 de noviembre.");
	}

	@ParameterizedTest
	@CsvSource({ "2026-10-28,2026-10-29", "2026-11-14,2026-11-15", "2026-11-12,2026-11-14" })
	void hotelDentroDeLaVentana(String llegada, String salida) {
		assertThat(validar(conHotel(llegada, salida, false, null))).isEmpty();
	}

	@Test
	void hotelConSalidaIgualALlegada() {
		assertError(conHotel("2026-11-12", "2026-11-12", false, null), CodigoError.HOTEL_INVALIDO,
				"La salida del hotel debe ser después de la llegada.", "preEventHotel.checkOut");
		assertThat(error(conHotel("2026-11-12", "2026-11-10", false, null)).detail())
				.isEqualTo("La salida del hotel debe ser después de la llegada.");
	}

	@Test
	void hotelConSalidaDespuesDelInicioDelEvento() {
		assertError(conHotel("2026-11-14", "2026-11-16", false, null), CodigoError.HOTEL_INVALIDO,
				"La salida del hotel no puede ser después del 15 de noviembre.", "preEventHotel.checkOut");
	}

	@ParameterizedTest
	@CsvSource({ "'',2026-11-14", "2026-11-12,''", "2026-11-31,2026-11-14", "12/11/2026,14/11/2026" })
	void hotelConFechasInvalidas(String llegada, String salida) {
		assertError(conHotel(llegada, salida, false, null), CodigoError.HOTEL_INVALIDO,
				"Elige las fechas de llegada y salida del hotel.",
				llegada.isEmpty() || llegada.equals("2026-11-31") || llegada.contains("/") ? "preEventHotel.checkIn"
						: "preEventHotel.checkOut");
	}

	@Test
	void hotelConAcompananteSinAcompanante() {
		assertError(conHotel("2026-11-12", "2026-11-14", true, null), CodigoError.HOTEL_INVALIDO,
				"Solo puedes sumar al acompañante al hotel si llevas acompañante.", "preEventHotel.withCompanion");
		assertThat(validar(conHotel("2026-11-12", "2026-11-14", true, Datos.acompanante()))).isEmpty();
	}

	@Test
	void hotelYaNoSePuedeReservarElDiaDelEvento() {
		var e = ValidadorHotel.validar(new HotelSolicitado("2026-11-15", "2026-11-16", false),
				false, LocalDate.parse("2026-11-15"), Datos.EVENTO).orElseThrow();
		assertThat(e.detail()).isEqualTo("Ya no se puede reservar hotel antes del evento.");
	}

	@Test
	void hotelDeCeroNochesNoTieneNoches() {
		// "Al menos una noche" queda como segunda barrera: la regla de salida posterior la antecede.
		assertThat(ValidadorHotel.noches("2026-11-12", "2026-11-12")).isZero();
		assertThat(ValidadorHotel.noches("2026-11-12", "2026-11-10")).isZero();
		assertThat(ValidadorHotel.noches("2026-11-12", "2026-11-14")).isEqualTo(2);
		assertThat(ValidadorHotel.noches("x", "2026-11-14")).isZero();
	}

	// --- acompañante

	@Test
	void acompananteConKitSinTalla() {
		assertError(conAcompanante(new AcompananteRegla(Datos.persona(), true, null)), CodigoError.TALLA_INVALIDA,
				"Elige la talla de camisa del acompañante.", "companion.shirtSize");
		assertThat(error(conAcompanante(new AcompananteRegla(Datos.persona(), true, "Z"))).code())
				.isEqualTo(CodigoError.TALLA_INVALIDA);
	}

	@Test
	void acompananteSinKitConTalla() {
		assertError(conAcompanante(new AcompananteRegla(Datos.persona(), false, "M")), CodigoError.TALLA_INVALIDA,
				"El acompañante sin kit no lleva talla de camisa.", "companion.shirtSize");
	}

	@Test
	void acompananteConKitYTallaPasa() {
		assertThat(validar(conAcompanante(new AcompananteRegla(Datos.persona(), true, "L")))).isEmpty();
	}

	@Test
	void acompananteConDatosInvalidosUsaPrefijoYTextoDelAcompanante() {
		var a = new AcompananteRegla(PB.acompanante().nombres("Carlos9").build(), false, null);
		assertError(conAcompanante(a), CodigoError.NOMBRE_INVALIDO,
				"Escribe nombres y apellidos del acompañante solo con letras (2 a 40 caracteres).",
				"companion.firstName");
	}

	@Test
	void acompananteConElMismoDocumento() {
		assertError(conAcompanante(Datos.titular(), PB.acompanante().documento("1065123456").build()),
				CodigoError.ACOMPANANTE_REPETIDO,
				"El acompañante no puede tener el mismo documento que el participante.", "companion.document");
	}

	@Test
	void acompananteConElMismoDocumentoEscritoDistintoEnMayusculas() {
		var titular = PB.titular().tipoDocumento("Pasaporte").documento("AB123456").build();
		var acompanante = PB.acompanante().tipoDocumento("Pasaporte").documento("ab123456").build();
		assertThat(error(conAcompanante(titular, acompanante)).detail())
				.isEqualTo("El acompañante no puede tener el mismo documento que el participante.");
	}

	@Test
	void acompananteConElMismoCorreo() {
		assertError(conAcompanante(Datos.titular(), PB.acompanante().correo("ana@example.com").build()),
				CodigoError.ACOMPANANTE_REPETIDO, "El acompañante debe tener un correo distinto al del participante.",
				"companion.email");
	}

	@Test
	void acompananteConElMismoCelular() {
		assertError(conAcompanante(Datos.titular(), PB.acompanante().telefono("CO", "3001234567").build()),
				CodigoError.ACOMPANANTE_REPETIDO, "El acompañante debe tener un celular distinto al del participante.",
				"companion.phone.number");
	}

	@Test
	void contactoDelTitularEsElAcompanantePorCelular() {
		var titular = PB.titular().contacto("LUIS PEREZ", "CO", "3109998877", "Hermano o hermana").build();
		assertError(conAcompanante(titular, Datos.persona()), CodigoError.ACOMPANANTE_REPETIDO,
				"El contacto de emergencia del participante no puede ser el acompañante.",
				"emergencyContact.phone.number");
	}

	@Test
	void contactoDelAcompananteEsElTitularPorCelular() {
		var acompanante = PB.acompanante().contacto("MARTA ROJAS", "CO", "3001234567", "Madre").build();
		assertError(conAcompanante(Datos.titular(), acompanante), CodigoError.ACOMPANANTE_REPETIDO,
				"El contacto de emergencia del acompañante no puede ser el participante.",
				"companion.emergencyContact.phone.number");
	}

	@Test
	void contactoDelTitularEsElAcompanantePorNombre() {
		var titular = PB.titular().contacto("CARLOS ROJAS DIAZ", "CO", "3157654321", "Hermano o hermana").build();
		assertError(conAcompanante(titular, Datos.persona()), CodigoError.ACOMPANANTE_REPETIDO,
				"El contacto de emergencia del participante no puede ser el acompañante.", "emergencyContact.name");
	}

	@Test
	void contactoDelAcompananteEsElTitularPorNombre() {
		var acompanante = PB.acompanante().contacto("ANA MARIA PEREZ GOMEZ", "CO", "3201112233", "Madre").build();
		assertError(conAcompanante(Datos.titular(), acompanante), CodigoError.ACOMPANANTE_REPETIDO,
				"El contacto de emergencia del acompañante no puede ser el participante.",
				"companion.emergencyContact.name");
	}

	// --- plan

	@Test
	void planFueraDeCatalogo() {
		assertError(reserva(List.of(), null, "mensual", null, null), CodigoError.PLAN_INVALIDO,
				"Elige la forma de pago.", "plan");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-10-27", "2026-11-10", "2026-11-31", "basura", "" })
	void apartadoConFechaFueraDeRango(String fecha) {
		assertError(reserva(List.of(), null, "apartado", fecha, null), CodigoError.FECHA_COMPROMISO_INVALIDA,
				"Elige la fecha en que pagarás el restante.", "commitmentDate");
	}

	@Test
	void apartadoSinFecha() {
		assertError(reserva(List.of(), null, "apartado", null, null), CodigoError.FECHA_COMPROMISO_INVALIDA,
				"Elige la fecha en que pagarás el restante.", "commitmentDate");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-10-28", "2026-11-05", "2026-11-09" })
	void apartadoConFechaEnRangoPasa(String fecha) {
		assertThat(validar(reserva(List.of(), null, "apartado", fecha, null))).isEmpty();
	}

	@Test
	void apartadoDespuesDelCierreOfreceSoloPagarElTotal() {
		var e = ValidadorPlan.validar("apartado", "2026-11-12", LocalDate.parse("2026-11-10"), Datos.EVENTO)
				.orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.FECHA_COMPROMISO_INVALIDA);
		assertThat(e.detail()).isEqualTo("Ya pasó la fecha límite para apartar; elige pagar el total.");
	}

	@Test
	void totalConFechaNoLlevaFecha() {
		assertError(reserva(List.of(), null, "total", "2026-11-05", null), CodigoError.FECHA_COMPROMISO_INVALIDA,
				"El pago total no lleva fecha para el restante.", "commitmentDate");
	}

	@Test
	void laFechaDeApartadoParteDeLaAperturaSiHoyEsAnterior() {
		var hoy = LocalDate.parse("2026-10-01");
		assertThat(ValidadorPlan.validar("apartado", "2026-10-28", hoy, Datos.EVENTO)).isEmpty();
		assertThat(ValidadorPlan.validar("apartado", "2026-10-15", hoy, Datos.EVENTO).orElseThrow().code())
				.isEqualTo(CodigoError.FECHA_COMPROMISO_INVALIDA);
	}

	// --- documentos duplicados

	@Test
	void documentoDelTitularYaReservado() {
		var e = ValidadorReserva.validar(base(), ctx("2026-10-28", Set.of("1065123456"), 60)).orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.DOCUMENTO_DUPLICADO);
		assertThat(e.detail()).isEqualTo("El documento 1065123456 ya tiene una reserva registrada. Si no fuiste tú, comunícate con la organización para que la revisen.");
		assertThat(e.campo()).isEqualTo("document");
	}

	@Test
	void documentoDelAcompananteYaReservado() {
		var e = ValidadorReserva
				.validar(conAcompanante(Datos.acompanante()), ctx("2026-10-28", Set.of("1065999888"), 60))
				.orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.DOCUMENTO_DUPLICADO);
		assertThat(e.campo()).isEqualTo("companion.document");
	}

	@Test
	void documentosSeComparanSinEspaciosNiMayusculas() {
		var titular = PB.titular().tipoDocumento("Pasaporte").documento("ab123456").build();
		var e = ValidadorReserva.validar(Datos.conTitular(base(), titular), ctx("2026-10-28", Set.of(" AB 123456"), 60))
				.orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.DOCUMENTO_DUPLICADO);
		assertThat(e.detail()).isEqualTo("El documento ab123456 ya tiene una reserva registrada. Si no fuiste tú, comunícate con la organización para que la revisen.");
	}

	@Test
	void elMensajeDeDuplicadoNoSeVuelcaEnToString() {
		var e = ValidadorReserva.validar(base(), ctx("2026-10-28", Set.of("1065123456"), 60)).orElseThrow();
		assertThat(e.toString()).doesNotContain("1065123456");
	}

	// --- cupo del páramo

	@Test
	void sinCuposEnElParamo() {
		var e = ValidadorReserva.validar(base(), ctx("2026-10-28", Set.of(), 0)).orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.SIN_CUPO_PARAMO);
		assertThat(e.detail())
				.isEqualTo("Los cupos del páramo están agotados: elige hotel en Manaure o regreso a casa.");
	}

	@Test
	void soloQuedaUnCupoParaDosPersonas() {
		var e = ValidadorReserva.validar(conAcompanante(Datos.acompanante()), ctx("2026-10-28", Set.of(), 1))
				.orElseThrow();
		assertThat(e.detail()).isEqualTo("Solo queda 1 cupo en el páramo y tu reserva necesita 2."
				+ " Elige otra estadía para alguno de los dos.");
	}

	@Test
	void hotelYRegresoACasaNoGastanCupo() {
		var titular = PB.titular().estadia("Hotel en Manaure").build();
		var acompanante = PB.acompanante().estadia("Regreso a casa").build();
		assertThat(ValidadorReserva.validar(conAcompanante(titular, acompanante), ctx("2026-10-28", Set.of(), 0)))
				.isEmpty();
	}

	@Test
	void soloUnoEnParamoCabeConUnCupo() {
		var acompanante = PB.acompanante().estadia("Hotel en Manaure").build();
		assertThat(ValidadorReserva.validar(conAcompanante(Datos.titular(), acompanante),
				ctx("2026-10-28", Set.of(), 1))).isEmpty();
	}

	// --- orden de los errores (diseño §8)

	@Test
	void conVariosErroresDevuelveElPrimeroSegunElOrdenDelDiseno() {
		var titular = PB.titular().nombres("1").tipoDocumento("TI").documento("1234567890").nacimiento("2009-10-29")
				.contacto("LUIS PEREZ", "CO", "3157654321", "Amigo o amiga");
		var acompanante = new AcompananteRegla(PB.acompanante().nombres("1").build(), false, null);
		var hotelMalo = new HotelSolicitado("2026-11-15", "2026-11-16", false);
		var docs = Set.of("1234567890");

		// cierre antes que todo
		assertThat(ValidadorReserva.validar(
				new NuevaReserva(titular.build(), "Z", acompanante, List.of("x"), hotelMalo, "x", null),
				ctx("2026-11-10", docs, 0)).orElseThrow().code()).isEqualTo(CodigoError.INSCRIPCIONES_CERRADAS);

		var ctx = ctx("2026-10-28", docs, 0);
		var r = new NuevaReserva(titular.build(), "Z", acompanante, List.of("x"), hotelMalo, "x", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code()).isEqualTo(CodigoError.NOMBRE_INVALIDO);

		titular.nombres("ANA");
		r = new NuevaReserva(titular.build(), "Z", acompanante, List.of("x"), hotelMalo, "x", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code())
				.isEqualTo(CodigoError.MENOR_SIN_RESPONSABLE);

		titular.contacto("LUIS PEREZ", "CO", "3157654321", "Madre");
		r = new NuevaReserva(titular.build(), "Z", acompanante, List.of("x"), hotelMalo, "x", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code()).isEqualTo(CodigoError.TALLA_INVALIDA);

		r = new NuevaReserva(titular.build(), "M", acompanante, List.of("x"), hotelMalo, "x", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code()).isEqualTo(CodigoError.EXTRA_INVALIDO);

		r = new NuevaReserva(titular.build(), "M", acompanante, List.of(), hotelMalo, "x", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code()).isEqualTo(CodigoError.HOTEL_INVALIDO);

		r = new NuevaReserva(titular.build(), "M", acompanante, List.of(), null, "x", null);
		var e = ValidadorReserva.validar(r, ctx).orElseThrow();
		assertThat(e.code()).isEqualTo(CodigoError.NOMBRE_INVALIDO);
		assertThat(e.campo()).isEqualTo("companion.firstName");

		var acompananteOk = Datos.acompanante();
		r = new NuevaReserva(titular.build(), "M", acompananteOk, List.of(), null, "x", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code()).isEqualTo(CodigoError.PLAN_INVALIDO);

		r = new NuevaReserva(titular.build(), "M", acompananteOk, List.of(), null, "total", null);
		assertThat(ValidadorReserva.validar(r, ctx).orElseThrow().code()).isEqualTo(CodigoError.DOCUMENTO_DUPLICADO);

		assertThat(ValidadorReserva.validar(r, ctx("2026-10-28", Set.of(), 0)).orElseThrow().code())
				.isEqualTo(CodigoError.SIN_CUPO_PARAMO);

		assertThat(ValidadorReserva.validar(r, ctx("2026-10-28", Set.of(), 60))).isEmpty();
	}

	@Test
	void elAcompananteSeValidaDespuesDelHotel() {
		var acompananteMalo = new AcompananteRegla(PB.acompanante().nombres("1").build(), false, null);
		var r = reserva(List.of(), new HotelSolicitado("2026-11-15", "2026-11-16", false), "total", null,
				acompananteMalo);
		assertThat(error(r).code()).isEqualTo(CodigoError.HOTEL_INVALIDO);
	}

}
