package co.manaure.trekking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** HU-N04: más de 10 solicitudes por minuto desde la misma IP dan 429 (el reloj fijo mantiene la ventana). */
@TestPropertySource(properties = "app.rate-limit.registro-por-minuto=10")
class ReservaPublicaRateLimitTest extends ReservaPublicaBase {

	@Test
	void laSolicitudOnceDeLaMismaIpDa429ConRetryAfter() throws Exception {
		for (int i = 1; i <= 10; i++) {
			enviar("{}").andExpect(status().isBadRequest());
		}
		enviar("{}").andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("DEMASIADAS_SOLICITUDES"));

		// Otra IP no se ve afectada.
		mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON).content("{}")
				.with(r -> {
					r.setRemoteAddr("10.9.8.7");
					return r;
				})).andExpect(status().isBadRequest());
	}

}
