package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.modelo.PersonaRegla;

class ValidadorPersonaTest {

	private static Optional<ErrorRegla> validar(PersonaRegla p) {
		return ValidadorPersona.validar(p, Quien.TITULAR, Datos.HOY, Datos.CATALOGOS, Datos.EVENTO);
	}

	private static ErrorRegla error(PB p) {
		return validar(p.build()).orElseThrow();
	}

	private static void assertError(PB p, CodigoError code, String detail, String campo) {
		ErrorRegla e = error(p);
		assertThat(e.code()).isEqualTo(code);
		assertThat(e.detail()).isEqualTo(detail);
		assertThat(e.campo()).isEqualTo(campo);
	}

	@Test
	void personaValidaNoDaError() {
		assertThat(validar(Datos.titular())).isEmpty();
		assertThat(validar(Datos.persona())).isEmpty();
	}

	// --- nombres (llegan ya normalizados: MAYÚSCULAS, sin tildes, la Ñ se conserva)

	@ParameterizedTest
	@ValueSource(strings = { "MARIA JOSE", "D'ANGELO", "ANA-LUCIA", "ÑANDU PEÑA", "JO", "LEFEVRE" })
	void nombresValidos(String nombre) {
		assertThat(validar(PB.titular().nombres(nombre).build())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "ANA2", "A", "ANA  MARIA", " ANA", "ANA ", "ANA_", "ANA.", "-ANA", "ANA--MARIA", "ANA - MARIA",
			"ANA@", "Ana", "MARÍA", "ИВАН" })
	void nombresInvalidos(String nombre) {
		assertError(PB.titular().nombres(nombre), CodigoError.NOMBRE_INVALIDO,
				"Escribe nombres y apellidos del participante solo con letras (2 a 40 caracteres).", "firstName");
	}

	@Test
	void nombreDe41CaracteresEsInvalidoYDe40Valido() {
		assertError(PB.titular().nombres("A".repeat(41)), CodigoError.NOMBRE_INVALIDO,
				"Escribe nombres y apellidos del participante solo con letras (2 a 40 caracteres).", "firstName");
		assertThat(validar(PB.titular().nombres("A".repeat(40)).build())).isEmpty();
	}

	@Test
	void apellidoInvalidoSeReportaEnLastName() {
		assertThat(error(PB.titular().apellidos("GOMEZ3")).campo()).isEqualTo("lastName");
		assertThat(error(PB.titular().apellidos("A".repeat(41))).campo()).isEqualTo("lastName");
	}

	// --- fecha de nacimiento

	@ParameterizedTest
	@ValueSource(strings = { "", "1990-13-01", "2026-10-29", "1900-01-01", "14/05/1990" })
	void fechaDeNacimientoInvalida(String fecha) {
		assertError(PB.titular().nacimiento(fecha), CodigoError.FECHA_NACIMIENTO_INVALIDA,
				"Revisa la fecha de nacimiento del participante.", "birthDate");
	}

	@Test
	void edadMaximaEs110() {
		// 110 años cumplidos a hoy es válido; 111 no (tipo CE para esquivar la edad mínima de la CC).
		assertThat(validar(PB.titular().tipoDocumento("CE").nacimiento("1916-10-28").build())).isEmpty();
		assertThat(error(PB.titular().tipoDocumento("CE").nacimiento("1915-10-27")).code())
				.isEqualTo(CodigoError.FECHA_NACIMIENTO_INVALIDA);
	}

	// --- género y ciudad

	@Test
	void generoFueraDeCatalogo() {
		assertError(PB.titular().genero("X"), CodigoError.GENERO_INVALIDO, "Elige el género del participante.",
				"gender");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Valledupar", "valledupar", "Sta. Marta", "San Jose de la Sierra", "Cucuta", "Rio-Hacha",
			"Bogota D.C.", "Ñapinda", "L'Aquila" })
	void ciudadesValidas(String ciudad) {
		assertThat(validar(PB.titular().ciudad(ciudad).build())).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "Ci", "Bogota1", "Sta.. Marta", "Bogotá", "Bogota, D.C.", ".Bogota" })
	void ciudadesInvalidas(String ciudad) {
		assertError(PB.titular().ciudad(ciudad), CodigoError.CIUDAD_INVALIDA,
				"Escribe la ciudad del participante solo con letras.", "city");
	}

	@Test
	void ciudadDe41CaracteresEsInvalidaYDe40Valida() {
		assertThat(error(PB.titular().ciudad("a".repeat(41))).code()).isEqualTo(CodigoError.CIUDAD_INVALIDA);
		assertThat(validar(PB.titular().ciudad("a".repeat(40)).build())).isEmpty();
	}

	// --- correo

	@Test
	void correoVacioPideEscribirlo() {
		assertError(PB.titular().correo(""), CodigoError.CORREO_INVALIDO,
				"Escribe el correo electrónico del participante.", "email");
	}

	@ParameterizedTest
	@ValueSource(strings = { "ana", "ana@", "@example.com", "ana@example", "ana..x@example.com", "ana@@example.com",
			"ana@example.c", "Ana@example.com", "ana @example.com" })
	void correoMalFormado(String correo) {
		assertError(PB.titular().correo(correo), CodigoError.CORREO_INVALIDO,
				"Revisa el correo electrónico del participante.", "email");
	}

	@Test
	void correoConDominioMalEscritoSugiereElCorrecto() {
		assertError(PB.titular().correo("ana@gmial.com"), CodigoError.CORREO_INVALIDO,
				"El correo del participante parece mal escrito: ¿quisiste decir @gmail.com?", "email");
		assertThat(error(PB.titular().correo("ana@hotmial.com")).detail()).endsWith("@hotmail.com?");
		assertThat(error(PB.titular().correo("ana@outlook.con")).detail()).endsWith("@outlook.com?");
	}

	@Test
	void correoDe100CaracteresEsValidoYDe101No() {
		String local = "a".repeat(100 - "@example.com".length());
		assertThat(validar(PB.titular().correo(local + "@example.com").build())).isEmpty();
		assertError(PB.titular().correo("a" + local + "@example.com"), CodigoError.CORREO_INVALIDO,
				"Revisa el correo electrónico del participante.", "email");
	}

	@Test
	void normalizarCorreoPasaAMinusculasYQuitaEspacios() {
		assertThat(ValidadorCorreo.normalizar(" Ana@Example.COM ")).isEqualTo("ana@example.com");
		assertThat(ValidadorCorreo.validar(ValidadorCorreo.normalizar("Ana@Example.COM"), "del participante",
				"email")).isEmpty();
	}

	// --- teléfono del titular

	@Test
	void telefonoDelTitularNoAdmiteFijo() {
		assertError(PB.titular().telefono("CO", "6011234567"), CodigoError.TELEFONO_INVALIDO,
				"El celular del participante no es válido para Colombia (ejemplo: 300 123 4567).", "phone.number");
	}

	// --- EPS

	@Test
	void epsFueraDeCatalogo() {
		assertError(PB.titular().eps("Inventada", null), CodigoError.EPS_INVALIDA, "Elige la EPS del participante.",
				"eps.entity");
	}

	@Test
	void epsOtraSinNombrePideElNombre() {
		assertError(PB.titular().eps("Otra", null), CodigoError.EPS_INVALIDA,
				"Escribe el nombre de la EPS del participante.", "eps.other");
		assertThat(error(PB.titular().eps("Otra", "A1")).detail())
				.isEqualTo("Escribe el nombre de la EPS del participante.");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Medimas", "Salud Vida", "Ñoño Salud" })
	void epsOtraConNombreValidoPasa(String nombre) {
		assertThat(validar(PB.titular().eps("Otra", nombre).build())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Ab", "Salud2", "Sanitas S.A.", "Salud  Vida", " Salud", "Medimás" })
	void epsOtraConNombreInvalido(String nombre) {
		assertThat(error(PB.titular().eps("Otra", nombre)).detail())
				.isEqualTo("Escribe el nombre de la EPS del participante.");
	}

	@Test
	void epsOtraDe40CaracteresEsValidaYDe41No() {
		assertThat(validar(PB.titular().eps("Otra", "a".repeat(40)).build())).isEmpty();
		assertThat(error(PB.titular().eps("Otra", "a".repeat(41))).code()).isEqualTo(CodigoError.EPS_INVALIDA);
	}

	@Test
	void epsDelCatalogoConNombreNoNuloSeRevisa() {
		assertError(PB.titular().eps("Nueva EPS", "Algo"), CodigoError.EPS_INVALIDA,
				"Revisa la EPS del participante.", "eps.other");
	}

	// --- contacto de emergencia

	@ParameterizedTest
	@ValueSource(strings = { "LUIS", "LU P", "LUIS2 PEREZ", "", "Luis Perez" })
	void contactoSinNombreYApellido(String nombre) {
		assertError(PB.titular().contacto(nombre, "CO", "3157654321", "Madre"),
				CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
				"Escribe nombre y apellido del contacto de emergencia del participante.",
				"emergencyContact.name");
	}

	@Test
	void contactoDe80CaracteresEsValidoYDe81No() {
		assertThat(validar(PB.titular().contacto("A".repeat(39) + " " + "B".repeat(40), "CO", "3157654321", "Madre")
				.build())).isEmpty();
		assertThat(error(PB.titular().contacto("A".repeat(40) + " " + "B".repeat(40), "CO", "3157654321", "Madre"))
				.code()).isEqualTo(CodigoError.CONTACTO_EMERGENCIA_INVALIDO);
	}

	@Test
	void contactoConLaEnieSeRespetaNoEsLoMismoQueConLaN() {
		var distinto = PB.titular().apellidos("PEÑA").contacto("ANA MARIA PENA", "CO", "3157654321", "Madre");
		assertThat(validar(distinto.build())).isEmpty();
	}

	@Test
	void contactoConElMismoNombreQueLaPersonaSinTildesNiMayusculas() {
		assertError(PB.titular().contacto("ANA MARIA PEREZ GOMEZ", "CO", "3157654321", "Madre"),
				CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
				"El contacto de emergencia del participante debe ser otra persona.", "emergencyContact.name");
	}

	@Test
	void contactoConElMismoTelefonoQueLaPersona() {
		assertError(PB.titular().contacto("LUIS PEREZ", "CO", "3001234567", "Madre"),
				CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
				"El teléfono del contacto de emergencia del participante debe ser distinto al suyo propio.",
				"emergencyContact.phone.number");
	}

	@ParameterizedTest
	@ValueSource(strings = { "6011234567", "6021234567", "6041234567", "6051234567", "6061234567", "6071234567",
			"6081234567" })
	void contactoAceptaFijosDeColombia(String fijo) {
		assertThat(validar(PB.titular().contacto("LUIS PEREZ", "CO", fijo, "Madre").build())).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "6001234567", "6031234567", "6091234567", "601123456", "60112345678" })
	void contactoRechazaFijosQueNoExisten(String fijo) {
		assertThat(error(PB.titular().contacto("LUIS PEREZ", "CO", fijo, "Madre")).code())
				.isEqualTo(CodigoError.TELEFONO_INVALIDO);
	}

	@Test
	void contactoAceptaFijoPeroNoUnNumeroInvalido() {
		assertThat(validar(PB.titular().contacto("LUIS PEREZ", "CO", "6011234567", "Madre").build())).isEmpty();
		assertError(PB.titular().contacto("LUIS PEREZ", "CO", "1234", "Madre"), CodigoError.TELEFONO_INVALIDO,
				"El teléfono del contacto de emergencia del participante no es válido para Colombia"
						+ " (ejemplo: 300 123 4567).",
				"emergencyContact.phone.number");
		assertThat(error(PB.titular().contacto("LUIS PEREZ", "XX", "1234", "Madre")).detail())
				.isEqualTo("Elige el país del teléfono del contacto de emergencia del participante.");
		assertThat(error(PB.titular().contacto("LUIS PEREZ", "CO", "12a4", "Madre")).detail())
				.isEqualTo("Escribe el teléfono del contacto de emergencia del participante solo con números.");
	}

	@Test
	void contactoConParentescoFueraDeCatalogo() {
		assertError(PB.titular().contacto("LUIS PEREZ", "CO", "3157654321", "Vecino"),
				CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
				"Elige el parentesco del contacto de emergencia del participante.",
				"emergencyContact.relationship");
	}

	// --- salud

	@ParameterizedTest
	@ValueSource(strings = { "A", " Asma", "Asma ", "" })
	void enfermedadInvalida(String detalle) {
		assertError(PB.titular().salud(detalle, null, null, true), CodigoError.SALUD_INVALIDA,
				"Describe la enfermedad del participante.", "health.illness");
	}

	@Test
	void alergiaYDiscapacidadInvalidas() {
		assertError(PB.titular().salud(null, "x", null, true), CodigoError.SALUD_INVALIDA,
				"Describe la alergia del participante.", "health.allergies");
		assertError(PB.titular().salud(null, null, "x".repeat(201), true), CodigoError.SALUD_INVALIDA,
				"Describe la discapacidad del participante.", "health.disability");
	}

	@Test
	void detalleDe200CaracteresEsValido() {
		assertThat(validar(PB.titular().salud("x".repeat(200), null, null, false).build())).isEmpty();
	}

	@Test
	void deporteSinIndicarPideIndicarlo() {
		assertError(PB.titular().salud(null, null, null, null), CodigoError.SALUD_INVALIDA,
				"Indica si el participante practica deporte.", "health.exercises");
		var e = ValidadorPersona.salud(PB.acompanante().salud(null, null, null, null).build().salud(),
				Quien.ACOMPANANTE).orElseThrow();
		assertThat(e.detail()).isEqualTo("Indica si el acompañante practica deporte.");
	}

	@Test
	void mensajesDeSaludNoIncluyenElTextoDelUsuario() {
		var e = error(PB.titular().salud("Asma crónica ", null, null, true));
		assertThat(e.detail()).doesNotContain("Asma");
		assertThat(e.toString()).doesNotContain("Asma");
	}

	// --- compromisos

	@Test
	void compromisosRequeridos() {
		assertError(PB.titular().compromisos(false, true), CodigoError.COMPROMISO_REQUERIDO,
				"El participante debe comprometerse a respetar las indicaciones y normas de las áreas protegidas.",
				"commitments.protectedAreas");
		assertError(PB.titular().compromisos(true, null), CodigoError.COMPROMISO_REQUERIDO,
				"El participante debe comprometerse a contribuir con el cuidado del medio ambiente.",
				"commitments.environment");
	}

	// --- consentimiento

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "ayer", "2026-13-45T00:00:00Z" })
	void consentimientoSinFechaValida(String fecha) {
		assertError(PB.titular().consentimiento(fecha, "TYC-2026-10-28", true),
				CodigoError.CONSENTIMIENTO_REQUERIDO,
				"Falta la aceptación de los términos y condiciones del participante.", "consent.acceptedAt");
	}

	@ParameterizedTest
	@ValueSource(strings = { "2026-10-28T15:20:11.482Z", "2026-10-28T10:20:11-05:00", "2026-10-28T10:20:11",
			"2026-10-28" })
	void consentimientoAceptaFormatosIso(String fecha) {
		assertThat(validar(PB.titular().consentimiento(fecha, "TYC-2026-10-28", true).build())).isEmpty();
	}

	@Test
	void versionDeTerminosDesactualizada() {
		assertError(PB.titular().consentimiento("2026-10-28T15:20:11.482Z", "TYC-2025-01-01", true),
				CodigoError.TERMINOS_DESACTUALIZADOS,
				"Los términos cambiaron: vuelve a leerlos y acéptalos del participante.", "consent.version");
	}

	@Test
	void usoDeImagenDebeSerBooleano() {
		assertError(PB.titular().consentimiento("2026-10-28T15:20:11.482Z", "TYC-2026-10-28", null),
				CodigoError.USO_IMAGEN_INVALIDO, "Revisa la autorización de uso de imagen del participante.",
				"consent.imageUse");
	}

	// --- estadía

	@Test
	void estadiaFueraDeCatalogo() {
		assertError(PB.titular().estadia("Carpa"), CodigoError.ESTADIA_INVALIDA, "Elige la estadía del participante.",
				"stay");
	}

	// --- orden dentro de la persona

	@Test
	void conVariosErroresDevuelveElPrimeroEnElOrdenDelDiseno() {
		var p = PB.titular().estadia("X").compromisos(false, false).genero("X").nombres("1");
		assertThat(error(p).code()).isEqualTo(CodigoError.NOMBRE_INVALIDO);
		p.nombres("ANA");
		assertThat(error(p).code()).isEqualTo(CodigoError.GENERO_INVALIDO);
		p.genero("Femenino");
		assertThat(error(p).code()).isEqualTo(CodigoError.COMPROMISO_REQUERIDO);
		p.compromisos(true, true);
		assertThat(error(p).code()).isEqualTo(CodigoError.ESTADIA_INVALIDA);
	}

	@Test
	void elAcompananteUsaTextoYPrefijoPropios() {
		var persona = PB.acompanante().correo("carlos@gmial.com").build();
		var e = ValidadorPersona.validar(persona, Quien.ACOMPANANTE, Datos.HOY, Datos.CATALOGOS, Datos.EVENTO)
				.orElseThrow();
		assertThat(e.detail()).isEqualTo("El correo del acompañante parece mal escrito: ¿quisiste decir @gmail.com?");
		assertThat(e.campo()).isEqualTo("companion.email");
	}

	@Test
	void toStringDelModeloNoExponeDatosPersonales() {
		assertThat(Datos.titular().toString()).doesNotContain("Penicilina").doesNotContain("1065123456");
		assertThat(Datos.titular().salud().toString()).doesNotContain("Penicilina");
		assertThat(Datos.reserva().toString()).doesNotContain("Ana");
	}

}
