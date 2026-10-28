package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** H4 con PostgreSQL real: el tope diario por IP cuenta también las solicitudes inválidas y audita una sola alerta. */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "app.rate-limit.reservas-por-ip-por-dia=20")
class ReservaPublicaTopeIpTest extends ReservaPublicaBase {

	private ResultActions desde(String ip, String json) throws Exception {
		return mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON).content(json).with(r -> {
			r.setRemoteAddr(ip);
			return r;
		}));
	}

	private int alertas() {
		return jdbc.queryForObject("select count(*) from auditoria where accion = 'ALERTA_RESERVAS_IP'",
				Integer.class);
	}

	@Test
	void laSolicitud21DaTooManyRequestsYLaAlertaSeAudita50VecesSoloUna(CapturedOutput salida) throws Exception {
		for (int i = 1; i <= 20; i++) {
			desde("198.51.100.7", "{}").andExpect(status().isBadRequest());
		}
		desde("198.51.100.7", "{\"firstName\":\"Ana María\"}").andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("DEMASIADAS_SOLICITUDES"))
				.andExpect(jsonPath("$.detail").value("Demasiadas solicitudes. Intenta de nuevo en un momento."));
		for (int i = 22; i <= 50; i++) {
			desde("198.51.100.7", "{}").andExpect(status().isTooManyRequests());
		}

		assertThat(alertas()).isEqualTo(1);
		var fila = jdbc.queryForMap("select * from auditoria where accion = 'ALERTA_RESERVAS_IP'");
		assertThat(fila.get("administrador_id")).isNull();
		assertThat(fila.get("entidad")).isEqualTo("RESERVA");
		assertThat((String) fila.get("entidad_ref")).startsWith("IP-").doesNotContain("198.51");
		assertThat(salida.getAll()).contains("ALERTA: la IP ")
				.contains("superó 20 solicitudes de reserva en 24 horas").doesNotContain("198.51.100.7")
				.doesNotContain("Ana María");
		assertThat(salida.getAll().split("ALERTA: la IP ", -1)).hasSize(2);

		// Otra IP no se ve afectada.
		desde("198.51.100.8", "{}").andExpect(status().isBadRequest());
	}

	@Test
	void laVentanaSeReiniciaAlAvanzarElRelojVeinticuatroHoras() throws Exception {
		for (int i = 1; i <= 21; i++) {
			desde("198.51.100.20", "{}");
		}
		desde("198.51.100.20", "{}").andExpect(status().isTooManyRequests());
		reloj.fijar(RelojPrueba.INICIAL.plus(Duration.ofHours(24)));
		desde("198.51.100.20", "{}").andExpect(status().isBadRequest());
	}

	@Test
	void laReservaValidaTambienCuentaYElTopeNoAfectaOtrasRutas() throws Exception {
		for (int i = 1; i <= 20; i++) {
			desde("198.51.100.30", "{}");
		}
		desde("198.51.100.30", ReservaJson.titular().json()).andExpect(status().isTooManyRequests());
		assertThat(contar("inscripcion")).isZero();
		mvc.perform(get("/api/v1/config").with(r -> {
			r.setRemoteAddr("198.51.100.30");
			return r;
		})).andExpect(status().isOk());
	}

}
