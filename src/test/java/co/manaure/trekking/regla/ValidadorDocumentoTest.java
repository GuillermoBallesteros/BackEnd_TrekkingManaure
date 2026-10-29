package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.manaure.trekking.comun.error.CodigoError;

class ValidadorDocumentoTest {

	private static Optional<ErrorRegla> validar(String tipo, String documento, String nacimiento) {
		var persona = PB.titular().tipoDocumento(tipo).documento(documento).nacimiento(nacimiento).build();
		return ValidadorDocumento.validar(persona, Quien.TITULAR, Datos.HOY, Datos.CATALOGOS);
	}

	/** Un documento válido y su edad típica por cada uno de los 6 tipos. */
	@ParameterizedTest(name = "{0} {1} con nacimiento {2} es válido")
	@CsvSource({ "CC,1065123456,1990-05-14", "CC,54876234,1990-05-14", "CC,1098726541,1990-05-14",
			"CC,123456789,1990-05-14", "CC,12345678,2008-10-28", "CE,123456,1990-05-14", "CE,1234567890,1990-05-14",
			"TI,1234567890,2015-01-01", "TI,12345678901,2008-05-01", "RC,1234567890,2021-01-01",
			"Pasaporte,AB1234,1990-05-14", "Pasaporte,AB1234567890,1990-05-14", "PPT,12345,1990-05-14",
			"PPT,12345678,1990-05-14" })
	void documentoValido(String tipo, String documento, String nacimiento) {
		assertThat(validar(tipo, documento, nacimiento)).isEmpty();
	}

	@ParameterizedTest(name = "{0} {1} inválido: {3}")
	@CsvSource({ "CC,12345,1990-05-14,entre 8 y 10 dígitos", "CC,1234567,1990-05-14,entre 8 y 10 dígitos",
			"CC,12345678901,1990-05-14,entre 8 y 10 dígitos", "CC,1.098.726.541,1990-05-14,entre 8 y 10 dígitos",
			"CC,12AB567890,1990-05-14,entre 8 y 10 dígitos", "CC,0987654321,1990-05-14,entre 8 y 10 dígitos",
			"CC,098726541,1990-05-14,entre 8 y 10 dígitos", "CE,12345,1990-05-14,entre 6 y 10 dígitos",
			"CE,12345678901,1990-05-14,entre 6 y 10 dígitos", "TI,123456789,2015-01-01,10 u 11 dígitos",
			"RC,123456789,2021-01-01,10 dígitos", "Pasaporte,AB123,1990-05-14,entre 6 y 12 letras o números",
			"Pasaporte,AB12345678901,1990-05-14,entre 6 y 12 letras o números",
			"Pasaporte,AB 1234,1990-05-14,entre 6 y 12 letras o números",
			"PPT,1234,1990-05-14,entre 5 y 8 dígitos", "PPT,123456789,1990-05-14,entre 5 y 8 dígitos",
			"PPT,ABC12,1990-05-14,entre 5 y 8 dígitos" })
	void documentoConFormatoInvalido(String tipo, String documento, String nacimiento, String ayuda) {
		var error = validar(tipo, documento, nacimiento).orElseThrow();
		assertThat(error.code()).isEqualTo(CodigoError.DOCUMENTO_INVALIDO);
		assertThat(error.detail()).isEqualTo("El documento del participante no es válido: " + ayuda + ".");
		assertThat(error.campo()).isEqualTo("document");
	}

	@Test
	void cedulaDeCincoDigitosUsaElTextoExactoDelFront() {
		assertThat(validar("CC", "12345", "1990-05-14").orElseThrow().detail())
				.isEqualTo("El documento del participante no es válido: entre 8 y 10 dígitos.");
	}

	@Test
	void tarjetaDeIdentidadConPersonaDe6AnosEsIncompatible() {
		var error = validar("TI", "1234567890", "2020-01-01").orElseThrow();
		assertThat(error.code()).isEqualTo(CodigoError.DOCUMENTO_EDAD_INCOMPATIBLE);
		assertThat(error.detail()).isEqualTo("La TI es para personas de 7 años o más: revisa el tipo de documento"
				+ " o la fecha de nacimiento del participante.");
	}

	@Test
	void registroCivilConPersonaDe8AnosEsIncompatible() {
		var error = validar("RC", "1234567890", "2018-01-01").orElseThrow();
		assertThat(error.code()).isEqualTo(CodigoError.DOCUMENTO_EDAD_INCOMPATIBLE);
		assertThat(error.detail()).isEqualTo("El RC es para personas de hasta 7 años: revisa el tipo de documento"
				+ " o la fecha de nacimiento del participante.");
	}

	@Test
	void cedulaConPersonaMenorDe18EsIncompatible() {
		assertThat(validar("CC", "1065123456", "2009-10-29").orElseThrow().detail())
				.isEqualTo("La CC es para personas de 18 años o más: revisa el tipo de documento"
						+ " o la fecha de nacimiento del participante.");
	}

	@Test
	void tarjetaDeIdentidadConPersonaDe19EsIncompatible() {
		assertThat(validar("TI", "1234567890", "2007-10-27").orElseThrow().detail())
				.isEqualTo("El TI es para personas de hasta 18 años: revisa el tipo de documento"
						+ " o la fecha de nacimiento del participante.");
	}

	@Test
	void tipoFueraDeCatalogoEsInvalido() {
		var error = validar("DNI", "12345678", "1990-05-14").orElseThrow();
		assertThat(error.code()).isEqualTo(CodigoError.TIPO_DOCUMENTO_INVALIDO);
		assertThat(error.detail()).isEqualTo("Elige un tipo de documento válido del participante.");
		assertThat(error.campo()).isEqualTo("documentType");
	}

	@Test
	void elAcompananteUsaSuPrefijoYSuTexto() {
		var persona = PB.acompanante().tipoDocumento("CC").documento("1").build();
		var error = ValidadorDocumento.validar(persona, Quien.ACOMPANANTE, Datos.HOY, Datos.CATALOGOS).orElseThrow();
		assertThat(error.detail()).isEqualTo("El documento del acompañante no es válido: entre 8 y 10 dígitos.");
		assertThat(error.campo()).isEqualTo("companion.document");
	}

}
