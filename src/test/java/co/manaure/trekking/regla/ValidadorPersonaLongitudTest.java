package co.manaure.trekking.regla;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import co.manaure.trekking.comun.error.CodigoError;

/** Hallazgo H5: la longitud se comprueba antes de aplicar expresiones regulares (textos de 1 MB). */
class ValidadorPersonaLongitudTest {

	private static final String ENORME = "a".repeat(1024 * 1024);
	private static final Duration TOPE = Duration.ofMillis(500);

	private enum Campo { NOMBRES, APELLIDOS, CIUDAD, EPS_OTRA, CONTACTO, DOCUMENTO, TELEFONO, CORREO, SALUD }

	private static PB con(Campo campo) {
		PB p = PB.titular();
		return switch (campo) {
			case NOMBRES -> p.nombres(ENORME);
			case APELLIDOS -> p.apellidos(ENORME);
			case CIUDAD -> p.ciudad(ENORME);
			case EPS_OTRA -> p.eps("Otra", ENORME);
			case CONTACTO -> p.contacto(ENORME, "CO", "3157654321", "Madre");
			case DOCUMENTO -> p.documento(ENORME);
			case TELEFONO -> p.telefono("CO", "3".repeat(1024 * 1024));
			case CORREO -> p.correo(ENORME + "@x.com");
			case SALUD -> p.salud(ENORME, null, null, true);
		};
	}

	@ParameterizedTest
	@EnumSource(Campo.class)
	void unTextoDeUnMegaSeRechazaRapidoConSuCodigo(Campo campo) {
		PB p = con(campo);
		Optional<ErrorRegla> error = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(TOPE,
				() -> ValidadorPersona.validar(p.build(), Quien.TITULAR, Datos.HOY, Datos.CATALOGOS, Datos.EVENTO));
		assertThat(error).isPresent();
		CodigoError esperado = switch (campo) {
			case NOMBRES, APELLIDOS -> CodigoError.NOMBRE_INVALIDO;
			case CIUDAD -> CodigoError.CIUDAD_INVALIDA;
			case EPS_OTRA -> CodigoError.EPS_INVALIDA;
			case CONTACTO -> CodigoError.CONTACTO_EMERGENCIA_INVALIDO;
			case DOCUMENTO -> CodigoError.DOCUMENTO_INVALIDO;
			case TELEFONO -> CodigoError.TELEFONO_INVALIDO;
			case CORREO -> CodigoError.CORREO_INVALIDO;
			case SALUD -> CodigoError.SALUD_INVALIDA;
		};
		assertThat(error.get().code()).isEqualTo(esperado);
		assertThat(error.get().detail()).doesNotContain("aaaa");
	}

	@Test
	void unaCiudadDeLongitudNormalSigueValida() {
		assertThat(ValidadorPersona.ciudad(PB.titular().ciudad("Valledupar").build(), Quien.TITULAR)).isEmpty();
	}

}
