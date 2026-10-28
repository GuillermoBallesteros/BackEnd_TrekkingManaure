package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Hallazgo H5: tope de tamaño del cuerpo y de listas/textos de entrada. */
class ReservaPublicaLimitesTest extends ReservaPublicaBase {

	@Test
	void cuerpoDe70KbDa413SinEco() throws Exception {
		ReservaJson json = ReservaJson.conAcompanante().set("firstName", "Z".repeat(70 * 1024));
		MvcResult r = enviar(json).andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("CUERPO_DEMASIADO_GRANDE"))
				.andExpect(jsonPath("$.detail").value("La solicitud es demasiado grande."))
				.andReturn();
		assertThat(r.getResponse().getContentAsString()).doesNotContain("ZZZZ");
		assertThat(contar("inscripcion")).isZero();
	}

	@Test
	void unaCiudadDeUnMegaSeRechazaSinProcesarla() throws Exception {
		enviar(ReservaJson.titular().set("city", "A".repeat(1024 * 1024))).andExpect(status().isPayloadTooLarge());
	}

	@Test
	void unaCiudadLargaPeroBajoElTopeDelCuerpoDa400() throws Exception {
		enviar(ReservaJson.titular().set("city", "A".repeat(5000))).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
	}

	@Test
	void milExtrasDan400SinProcesarlos() throws Exception {
		enviar(ReservaJson.titular().set("extras", Collections.nCopies(1000, "cuatrimoto")))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"));
		assertThat(contar("inscripcion")).isZero();
	}

	@Test
	void cuatroExtrasDan400() throws Exception {
		enviar(ReservaJson.titular().set("extras", List.of("cuatrimoto", "parapente", "parapenteAcompanante", "x")))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unaReservaCompletaConAcompananteCabeConHolgura() throws Exception {
		ReservaJson json = ReservaJson.conAcompanante();
		assertThat(json.json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan(16 * 1024);
		enviar(json).andExpect(status().isCreated());
	}

}
