package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.ValidadorTelefono.Deteccion.Estado;
import co.manaure.trekking.regla.ValidadorTelefono.TipoTelefono;
import co.manaure.trekking.regla.modelo.Telefono;

class ValidadorTelefonoTest {

	private static java.util.Optional<ErrorRegla> validar(String pais, String numero, TipoTelefono tipo) {
		return ValidadorTelefono.validar(new Telefono(pais, numero), tipo, "del participante", Datos.CATALOGOS,
				"phone");
	}

	/** Un celular válido y uno inválido por cada uno de los 13 países. */
	@ParameterizedTest(name = "{0}: celular {1} válido, {2} inválido")
	@CsvSource({ "CO,3001234567,2001234567", "VE,4121234567,3121234567", "EC,991234567,891234567",
			"PE,912345678,812345678", "MX,5512345678,0512345678", "US,2125550123,1125550123",
			"ES,612345678,512345678", "AR,91112345678,0111234567", "CL,912345678,812345678",
			"BR,11912345678,01912345678", "PA,61234567,51234567", "CR,83123456,13123456",
			"DO,8091234567,7091234567" })
	void celularValidoEInvalidoPorPais(String pais, String valido, String invalido) {
		assertThat(validar(pais, valido, TipoTelefono.MOVIL)).isEmpty();
		var error = validar(pais, invalido, TipoTelefono.MOVIL).orElseThrow();
		assertThat(error.code()).isEqualTo(CodigoError.TELEFONO_INVALIDO);
		assertThat(error.detail()).startsWith("El celular del participante no es válido para ");
		assertThat(error.campo()).isEqualTo("phone.number");
	}

	@Test
	void mensajeIncluyeNombreDelPaisYEjemplo() {
		assertThat(validar("CO", "2001234567", TipoTelefono.MOVIL).orElseThrow().detail())
				.isEqualTo("El celular del participante no es válido para Colombia (ejemplo: 300 123 4567).");
	}

	@ParameterizedTest(name = "{0}: fijo {1}")
	@CsvSource({ "CO,6011234567", "VE,2121234567", "EC,22123456", "PE,11234567", "ES,912345678", "CL,221234567",
			"BR,1112345678", "PA,2123456", "CR,22123456" })
	void fijoSoloSeAceptaComoCualquiera(String pais, String fijo) {
		assertThat(validar(pais, fijo, TipoTelefono.CUALQUIERA)).isEmpty();
		var error = validar(pais, fijo, TipoTelefono.MOVIL).orElseThrow();
		assertThat(error.detail()).startsWith("El celular del participante no es válido para ");
	}

	@ParameterizedTest(name = "{0} rechaza fijo")
	@CsvSource({ "MX,55123456,México,55 1234 5678", "US,21255501,Estados Unidos,212 555 0123",
			"AR,1123456,Argentina,9 11 1234 5678", "DO,80912345,República Dominicana,809 123 4567" })
	void paisesSinFijoLoRechazanEnElContacto(String pais, String fijo, String nombre, String ejemplo) {
		var error = validar(pais, fijo, TipoTelefono.CUALQUIERA).orElseThrow();
		assertThat(error.detail())
				.isEqualTo("El teléfono del participante no es válido para " + nombre + " (ejemplo: " + ejemplo + ").");
	}

	@Test
	void paisFueraDeCatalogoPidePais() {
		var error = validar("XX", "3001234567", TipoTelefono.MOVIL).orElseThrow();
		assertThat(error.detail()).isEqualTo("Elige el país del teléfono del participante.");
		assertThat(error.campo()).isEqualTo("phone.country");
		assertThat(validar(null, "3001234567", TipoTelefono.MOVIL).orElseThrow().detail())
				.isEqualTo("Elige el país del teléfono del participante.");
	}

	@ParameterizedTest
	@CsvSource({ "300 123 4567", "30012345a7", "+573001234567", "''" })
	void numeroConCaracteresNoNumericosEsInvalido(String numero) {
		var error = validar("CO", numero.equals("''") ? "" : numero, TipoTelefono.MOVIL).orElseThrow();
		assertThat(error.detail()).isEqualTo("Escribe el teléfono del participante solo con números.");
		assertThat(error.campo()).isEqualTo("phone.number");
	}

	@Test
	void phoneKeyEsIndicativoMasNumero() {
		assertThat(ValidadorTelefono.phoneKey(new Telefono("CO", "3001234567"), Datos.CATALOGOS))
				.isEqualTo("+573001234567");
		assertThat(ValidadorTelefono.phoneKey(new Telefono("US", "2125550123"), Datos.CATALOGOS))
				.isEqualTo("+12125550123");
		assertThat(ValidadorTelefono.phoneKey(new Telefono("XX", "123"), Datos.CATALOGOS)).isEqualTo("+123");
	}

	@ParameterizedTest
	@CsvSource({ "CO,+57 300 123 4567,3001234567", "CO,573001234567,3001234567", "CO,300-123-4567,3001234567",
			"CO,3001234567,3001234567", "XX,57 300,57300" })
	void normalizarQuitaElIndicativoEscritoDelante(String pais, String escrito, String esperado) {
		assertThat(ValidadorTelefono.normalizarNumero(pais, escrito, Datos.CATALOGOS)).isEqualTo(esperado);
	}

	@Test
	void detectaElPaisPorIndicativo() {
		var d = ValidadorTelefono.detectarPais("+57 300 123 4567", Datos.CATALOGOS);
		assertThat(d.estado()).isEqualTo(Estado.DETECTADO);
		assertThat(d.pais()).isEqualTo("CO");
		assertThat(d.resto()).isEqualTo("3001234567");
	}

	@Test
	void masUnoSeResuelvePorCodigoDeArea() {
		assertThat(ValidadorTelefono.detectarPais("+1 809 123 4567", Datos.CATALOGOS).pais()).isEqualTo("DO");
		assertThat(ValidadorTelefono.detectarPais("+1 212 555 0123", Datos.CATALOGOS).pais()).isEqualTo("US");
		assertThat(ValidadorTelefono.detectarPais("+1 21", Datos.CATALOGOS).estado()).isEqualTo(Estado.INCOMPLETO);
	}

	@Test
	void sinMasNiDobleCeroEsDesconocido() {
		assertThat(ValidadorTelefono.detectarPais("3001234567", Datos.CATALOGOS).estado())
				.isEqualTo(Estado.DESCONOCIDO);
		assertThat(ValidadorTelefono.detectarPais("0057 3001234567", Datos.CATALOGOS).pais()).isEqualTo("CO");
		assertThat(ValidadorTelefono.detectarPais("+999", Datos.CATALOGOS).estado()).isEqualTo(Estado.DESCONOCIDO);
		assertThat(ValidadorTelefono.detectarPais("+5", Datos.CATALOGOS).estado()).isEqualTo(Estado.INCOMPLETO);
	}

}
