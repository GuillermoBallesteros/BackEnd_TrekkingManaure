package co.manaure.trekking.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** HU-N15: petición a Brevo (cabeceras y cuerpo) y política de reintentos. */
class ClienteBrevoTest {

	private static final String URL = "https://api.brevo.test/v3";
	private static final String ENVIO = URL + "/smtp/email";
	private static final Destinatario ANA = new Destinatario("Ana Maria Perez Gomez", "ana@example.com");

	private MockRestServiceServer servidor;

	private ClienteBrevo cliente() {
		RestClient.Builder builder = RestClient.builder().baseUrl(URL);
		servidor = MockRestServiceServer.bindTo(builder).build();
		PropiedadesBrevo propiedades = new PropiedadesBrevo(true, "clave-prueba", URL,
				"manaureecoturisticointernacional@manaurevive.com", "ManaureVive", 50, 0, false, Map.of());
		return new ClienteBrevo(builder.build(), propiedades);
	}

	private static String creado(String mensaje) {
		return "{\"messageId\":\"" + mensaje + "\"}";
	}

	@Test
	void enviaLaPlantillaConRemitenteDestinatarioParametrosEtiquetasYClave() {
		ClienteBrevo cliente = cliente();
		servidor.expect(once(), requestTo(ENVIO)).andExpect(method(HttpMethod.POST))
				.andExpect(header("api-key", "clave-prueba")).andExpect(headerDoesNotExist("X-Sib-Sandbox"))
				.andExpect(jsonPath("$.sender.email").value("manaureecoturisticointernacional@manaurevive.com"))
				.andExpect(jsonPath("$.sender.name").value("ManaureVive"))
				.andExpect(jsonPath("$.to[0].email").value("ana@example.com"))
				.andExpect(jsonPath("$.to[0].name").value("Ana Maria Perez Gomez"))
				.andExpect(jsonPath("$.templateId").value(7)).andExpect(jsonPath("$.params.saldo").value("$300.000"))
				.andExpect(jsonPath("$.tags[0]").value("trekking-perija"))
				.andExpect(jsonPath("$.tags[1]").value("PAGO_HOY"))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
						.body(creado("<abc@smtp-relay.mailin.fr>")));

		String mensaje = cliente.enviar(ANA, 7, Map.of("saldo", "$300.000"), TipoAviso.PAGO_HOY);

		assertThat(mensaje).isEqualTo("<abc@smtp-relay.mailin.fr>");
		servidor.verify();
	}

	@Test
	void unErrorTemporalSeReintentaYElSegundoIntentoEnvia() {
		ClienteBrevo cliente = cliente();
		servidor.expect(once(), requestTo(ENVIO)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
		servidor.expect(once(), requestTo(ENVIO)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
		servidor.expect(once(), requestTo(ENVIO))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(creado("ok")));

		assertThat(cliente.enviar(ANA, 7, Map.of(), TipoAviso.PAGO_1_DIA)).isEqualTo("ok");
		servidor.verify();
	}

	@Test
	void tresErroresTemporalesFallanConElEstadoDeLaUltimaRespuesta() {
		ClienteBrevo cliente = cliente();
		servidor.expect(times(ClienteBrevo.MAX_INTENTOS), requestTo(ENVIO))
				.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> cliente.enviar(ANA, 7, Map.of(), TipoAviso.PAGO_1_DIA))
				.isInstanceOfSatisfying(ErrorBrevo.class, ex -> assertThat(ex.estado()).isEqualTo(429))
				.hasMessage("Brevo respondió 429.");
		servidor.verify();
	}

	@Test
	void unErrorDelClienteNoSeReintentaYElMensajeNoLlevaDatosDeLaPersona() {
		ClienteBrevo cliente = cliente();
		servidor.expect(once(), requestTo(ENVIO)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
				.contentType(MediaType.APPLICATION_JSON).body("{\"code\":\"invalid_parameter\"}"));

		assertThatThrownBy(() -> cliente.enviar(ANA, 7, Map.of(), TipoAviso.PAGO_RECHAZADO))
				.isInstanceOfSatisfying(ErrorBrevo.class, ex -> assertThat(ex.estado()).isEqualTo(400))
				.hasMessageNotContaining("ana@example.com").hasMessageNotContaining("Ana");
		servidor.verify();
	}

	@Test
	void activarSinClaveOSinRemitenteImpideArrancarYNombraLaVariable() {
		PropiedadesBrevo sinClave = new PropiedadesBrevo(true, " ", URL, "a@b.co", "X", 50, 0, false, Map.of());
		assertThatThrownBy(() -> ConfiguracionBrevo.validar(sinClave)).isInstanceOf(IllegalStateException.class)
				.hasMessage("BREVO_ACTIVO=true exige BREVO_API_KEY.");
		PropiedadesBrevo sinRemitente = new PropiedadesBrevo(true, "clave", URL, "", "X", 50, 0, false,
				Map.of());
		assertThatThrownBy(() -> ConfiguracionBrevo.validar(sinRemitente)).isInstanceOf(IllegalStateException.class)
				.hasMessage("BREVO_ACTIVO=true exige BREVO_REMITENTE_CORREO.");
	}

	@Test
	void unTipoSinPlantillaTieneIdCero() {
		PropiedadesBrevo propiedades = new PropiedadesBrevo(true, "clave", URL, "a@b.co", "X", 50, 0, false,
				Map.of("pago-hoy", 12L));
		assertThat(propiedades.plantilla(TipoAviso.PAGO_HOY)).isEqualTo(12);
		assertThat(propiedades.plantilla(TipoAviso.PAGO_VENCIDO)).isZero();
	}

}
