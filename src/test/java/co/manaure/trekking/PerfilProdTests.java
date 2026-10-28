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
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/** HU-D2: con el perfil prod no hay documentación de API ni detalles internos en los errores. */
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.profiles.active=prod",
		"app.seguridad.jwt-secret=clave-jwt-solo-para-pruebas-prod-0123456789-abcdefghij",
		"app.cors.origin=https://front.ejemplo.com",
		"app.admin.correo=admin-prueba@ejemplo.com",
		"app.admin.clave=clave-admin-prueba-1",
		"app.cifrado.salud-clave=1:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=" })
class PerfilProdTests {

	@Value("${local.server.port}")
	int port;

	@Autowired
	Environment env;

	private HttpResponse<String> enviar(HttpRequest.Builder b) throws Exception {
		return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> get(String path) throws Exception {
		return enviar(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)));
	}

	@Test
	void springdocNoResponde() throws Exception {
		for (String ruta : new String[] { "/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html" }) {
			HttpResponse<String> r = get(ruta);
			assertThat(r.statusCode()).isIn(401, 403, 404);
			assertThat(r.body()).doesNotContain("openapi");
		}
	}

	@Test
	void configuracionDeProduccion() {
		assertThat(env.getProperty("server.forward-headers-strategy")).isEqualTo("native");
		assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
		assertThat(env.getProperty("springdoc.api-docs.enabled")).isEqualTo("false");
	}

	@Test
	void errorDeValidacionNoIncluyeTrazas() throws Exception {
		HttpResponse<String> r = enviar(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{")));
		assertThat(r.statusCode()).isEqualTo(400);
		assertThat(r.body()).contains("DATOS_INVALIDOS").doesNotContain("trace").doesNotContain("Exception")
				.doesNotContain("at org.").doesNotContain("at co.");
	}

	@Test
	void errorDeRutaInexistenteNoIncluyeTrazas() throws Exception {
		HttpResponse<String> r = get("/api/v1/no-existe");
		assertThat(r.statusCode()).isIn(401, 403, 404);
		assertThat(r.body()).doesNotContain("trace").doesNotContain("Exception").doesNotContain("at org.");
	}

}
