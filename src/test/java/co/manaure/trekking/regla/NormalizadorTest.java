package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import co.manaure.trekking.regla.modelo.Telefono;

/** Ejemplos de normalización del documento de validaciones del formulario (sección 3). */
class NormalizadorTest {

	@ParameterizedTest(name = "nombre \"{0}\" se guarda como \"{1}\"")
	@CsvSource(delimiter = '|', value = { "María José|MARIA JOSE", "Muñoz Peña|MUÑOZ PEÑA", "Ana López|ANA LOPEZ",
			"Ürümqi|URUMQI", "Lefèvre|LEFEVRE", "João|JOAO", "Conceição|CONCEICAO", "Zoë|ZOE",
			"   ana    maria   |ANA MARIA", "D’Angelo|D'ANGELO", "ana‐maria|ANA-MARIA", "ñandú|ÑANDU",
			"PEÑA|PEÑA", "Straße|STRASSE" })
	void nombreSinTildesYEnMayusculas(String escrito, String guardado) {
		assertThat(Normalizador.nombre(escrito)).isEqualTo(guardado);
	}

	@Test
	void laEnieNoEsLoMismoQueLaN() {
		assertThat(Normalizador.nombre("Peña")).isEqualTo("PEÑA").isNotEqualTo(Normalizador.nombre("Pena"));
	}

	@Test
	void unaLetraConAcentoDescompuestaSeNormalizaIgual() {
		// "e" + U+0301 (acento suelto) y "n" + U+0303 (tilde suelta de la Ñ).
		assertThat(Normalizador.nombre("José")).isEqualTo("JOSE");
		assertThat(Normalizador.nombre("Peña")).isEqualTo("PEÑA");
	}

	@ParameterizedTest(name = "ciudad \"{0}\" se guarda como \"{1}\"")
	@CsvSource(delimiter = '|', value = { "Valledupar|Valledupar", "valledupar|valledupar", "Bogotá D.C.|Bogota D.C.",
			"Medellín|Medellin", "  Sta.   Marta |Sta. Marta", "Ñapindá|Ñapinda", "L’Aquila|L'Aquila" })
	void lugarSinTildesConservaLasMayusculas(String escrito, String guardado) {
		assertThat(Normalizador.lugar(escrito)).isEqualTo(guardado);
	}

	@ParameterizedTest(name = "documento \"{0}\" se guarda como \"{1}\"")
	@CsvSource(delimiter = '|', value = { "1.098.726.541|1098726541", "1 098 726 541|1098726541",
			"1098-726-541|1098726541", "ab-123 456|AB123456", "54876234|54876234", " 1098726541 |1098726541" })
	void documentoSinSeparadoresYEnMayusculas(String escrito, String guardado) {
		assertThat(Normalizador.documento(escrito)).isEqualTo(guardado);
	}

	@ParameterizedTest(name = "correo \"{0}\" se guarda como \"{1}\"")
	@CsvSource(delimiter = '|', value = { "Ana@Gmail.com|ana@gmail.com", " ana@gmail.com |ana@gmail.com",
			"ANA @ GMAIL.COM|ana@gmail.com" })
	void correoEnMinusculasSinEspacios(String escrito, String guardado) {
		assertThat(Normalizador.correo(escrito)).isEqualTo(guardado);
	}

	@ParameterizedTest(name = "teléfono \"{0}\" se guarda como \"{1}\"")
	@CsvSource(delimiter = '|', value = { "+57 300 123 4567|3001234567", "573001234567|3001234567",
			"(300) 123-4567|3001234567", "3001234567|3001234567" })
	void telefonoSoloDigitosSinIndicativo(String escrito, String guardado) {
		assertThat(Normalizador.telefono(new Telefono("CO", escrito), Datos.CATALOGOS).numero()).isEqualTo(guardado);
	}

	@Test
	void unTelefonoSinPaisConocidoSoloQuedaConDigitos() {
		assertThat(Normalizador.telefono(new Telefono("XX", "+57 300"), Datos.CATALOGOS).numero()).isEqualTo("57300");
	}

	@Test
	void textoQuitaEspaciosDeLosExtremosYReduceLosDobles() {
		assertThat(Normalizador.texto("  a   b\t\nc  ")).isEqualTo("a b c");
	}

	@NullSource
	@ParameterizedTest
	void nuloSigueSiendoNulo(String valor) {
		assertThat(Normalizador.texto(valor)).isNull();
		assertThat(Normalizador.nombre(valor)).isNull();
		assertThat(Normalizador.lugar(valor)).isNull();
		assertThat(Normalizador.documento(valor)).isNull();
		assertThat(Normalizador.correo(valor)).isNull();
		assertThat(Normalizador.telefono(null, Datos.CATALOGOS)).isNull();
	}

}
