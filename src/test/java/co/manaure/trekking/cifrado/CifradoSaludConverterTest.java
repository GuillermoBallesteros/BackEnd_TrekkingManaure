package co.manaure.trekking.cifrado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import co.manaure.trekking.api.dto.SaludDto;
import co.manaure.trekking.persona.FichaSalud;
import co.manaure.trekking.persona.Persona;

/** HU-N01: cifrado AES-256-GCM de la ficha de salud y validación de la clave (sin base de datos). */
class CifradoSaludConverterTest {

	// Claves de prueba (no son secretos reales).
	static final String CLAVE_1 = "1:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
	static final String CLAVE_2 = "2:ZmVkY2JhOTg3NjU0MzIxMGZlZGNiYTk4NzY1NDMyMTA=";

	private static CifradoSaludConverter convertidor(String actual, String anteriores) {
		return new CifradoSaludConverter(new ClaveCifrado(actual, anteriores));
	}

	@Test
	void guardaConFormatoVersionBase64SinElTextoOriginalYLoDevuelveAlLeer() {
		CifradoSaludConverter c = convertidor(CLAVE_1, "");

		String guardado = c.convertToDatabaseColumn("Penicilina");

		assertThat(guardado).matches("1:[A-Za-z0-9+/]+={0,2}").doesNotContain("Penicilina");
		// iv (12) + texto (10) + etiqueta (16)
		assertThat(Base64.getDecoder().decode(guardado.substring(2))).hasSize(12 + 10 + 16);
		assertThat(c.convertToEntityAttribute(guardado)).isEqualTo("Penicilina");
	}

	@Test
	void elMismoTextoCifradoDosVecesProduceValoresDistintos() {
		CifradoSaludConverter c = convertidor(CLAVE_1, "");

		String a = c.convertToDatabaseColumn("Asma");
		String b = c.convertToDatabaseColumn("Asma");

		assertThat(a).isNotEqualTo(b);
		assertThat(c.convertToEntityAttribute(a)).isEqualTo("Asma");
		assertThat(c.convertToEntityAttribute(b)).isEqualTo("Asma");
	}

	@Test
	void losNulosNoSeCifran() {
		CifradoSaludConverter c = convertidor(CLAVE_1, "");
		assertThat(c.convertToDatabaseColumn(null)).isNull();
		assertThat(c.convertToEntityAttribute(null)).isNull();
	}

	@Test
	void valorCifradoConLaClaveAnteriorSeDescifraSiEstaEnLaListaDeAnteriores() {
		String conVersion1 = convertidor(CLAVE_1, "").convertToDatabaseColumn("Diabetes");
		assertThat(conVersion1).startsWith("1:");

		CifradoSaludConverter rotado = convertidor(CLAVE_2, CLAVE_1);

		assertThat(rotado.convertToEntityAttribute(conVersion1)).isEqualTo("Diabetes");
		// Lo nuevo se cifra con la clave vigente.
		assertThat(rotado.convertToDatabaseColumn("Diabetes")).startsWith("2:");

		CifradoSaludConverter sinAnterior = convertidor(CLAVE_2, "");
		assertThatThrownBy(() -> sinAnterior.convertToEntityAttribute(conVersion1))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageNotContaining("Diabetes");
	}

	@Test
	void valorAlteradoNoSeDescifra() {
		CifradoSaludConverter c = convertidor(CLAVE_1, "");
		byte[] datos = Base64.getDecoder().decode(c.convertToDatabaseColumn("Rinitis").substring(2));
		datos[datos.length - 1] ^= 1;
		String alterado = "1:" + Base64.getEncoder().encodeToString(datos);

		assertThatThrownBy(() -> c.convertToEntityAttribute(alterado)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void laClaveEsObligatoriaYDebeTenerFormatoVersionBase64DeTreintaYDosBytes() {
		String quince = Base64.getEncoder().encodeToString(new byte[16]);
		for (String invalida : new String[] { null, "", "   ", "sinversion", "1:", ":AAAA", "x:" + quince, "0:" + quince,
				"1:!!!no-es-base64!!!", "1:" + quince, "1:" + Base64.getEncoder().encodeToString(new byte[33]) }) {
			assertThatThrownBy(() -> new ClaveCifrado(invalida, "")).as("clave [%s]", invalida)
					.isInstanceOf(IllegalStateException.class);
		}
		assertThat(new ClaveCifrado(CLAVE_1, "").versionActual()).isEqualTo(1);
	}

	@Test
	void losMensajesDeErrorNoRevelanLaClave() {
		String invalida = "1:" + Base64.getEncoder().encodeToString(new byte[16]);
		assertThatThrownBy(() -> new ClaveCifrado(invalida, ""))
				.hasMessageNotContaining(invalida.substring(2));
	}

	@Test
	void lasClavesAnterioresInvalidasORepetidasImpidenElArranque() {
		assertThatThrownBy(() -> new ClaveCifrado(CLAVE_2, "1:corta")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new ClaveCifrado(CLAVE_2, CLAVE_2)).isInstanceOf(IllegalStateException.class);
		assertThat(new ClaveCifrado(CLAVE_2, CLAVE_1 + " , ").clave(1)).isNotNull();
	}

	@Test
	void laAplicacionNoArrancaSinClaveOConClaveInvalida() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withUserConfiguration(ClaveCifrado.class, CifradoSaludConverter.class);

		runner.run(contexto -> assertThat(contexto).hasFailed());
		runner.withPropertyValues("app.cifrado.salud-clave=1:AAAA").run(contexto -> assertThat(contexto).hasFailed());
		runner.withPropertyValues("app.cifrado.salud-clave=" + CLAVE_1).run(contexto -> assertThat(contexto).hasNotFailed());
	}

	@Test
	void toStringDeFichaSaludYDeSuDtoNoMuestraValores() {
		Persona persona = new Persona("MARIA", "PEREZ", "CC", "12345678", java.time.LocalDate.of(1990, 5, 14),
				"Femenino", "CO", "3001234567", "maria@example.com", "Valledupar", "Nueva EPS", null);
		FichaSalud ficha = new FichaSalud(persona, "Asma severa", "Penicilina", "Visual", true);
		SaludDto dto = new SaludDto("Asma severa", "Penicilina", "Visual", true);

		for (String texto : new String[] { ficha.toString(), dto.toString(), persona.toString() }) {
			assertThat(texto).doesNotContain("Asma", "Penicilina", "Visual", "12345678", "MARIA", "maria@example.com");
		}
		assertThat(ficha.isTieneEnfermedad()).isTrue();
		assertThat(ficha.isTieneAlergias()).isTrue();
		assertThat(ficha.isTieneDiscapacidad()).isTrue();
	}

}
