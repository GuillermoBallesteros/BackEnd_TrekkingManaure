package co.manaure.trekking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ActuatorHealthTests {

	@Value("${local.server.port}")
	int port;

	private HttpResponse<String> get(String path) throws Exception {
		return HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
				HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void healthEsUp() throws Exception {
		HttpResponse<String> r = get("/actuator/health");
		assertThat(r.statusCode()).isEqualTo(200);
		assertThat(r.body()).contains("\"status\":\"UP\"");
	}

	@Test
	void otrosEndpointsDeActuatorNoEstanExpuestos() throws Exception {
		assertThat(get("/actuator/env").statusCode()).isIn(401, 403, 404);
	}

}
