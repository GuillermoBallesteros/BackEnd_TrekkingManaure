package co.manaure.trekking.notificacion;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Avisos por correo (HU-N15). Solo existe con {@code app.brevo.activo=true}; apagado no hay cliente, tarea ni
 * listener y nada se envía. Encendido sin {@code BREVO_API_KEY}, la API no arranca.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.brevo.activo", havingValue = "true")
@EnableConfigurationProperties(PropiedadesBrevo.class)
class ConfiguracionBrevo {

	@Bean
	ClienteBrevo clienteBrevo(PropiedadesBrevo propiedades) {
		validar(propiedades);
		SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
		fabrica.setConnectTimeout(Duration.ofSeconds(5));
		fabrica.setReadTimeout(Duration.ofSeconds(10));
		RestClient http = RestClient.builder().baseUrl(propiedades.url()).requestFactory(fabrica).build();
		return new ClienteBrevo(http, propiedades);
	}

	/** Los mensajes nombran la variable, nunca su valor. */
	static void validar(PropiedadesBrevo propiedades) {
		if (propiedades.apiKey() == null || propiedades.apiKey().isBlank()) {
			throw new IllegalStateException("BREVO_ACTIVO=true exige BREVO_API_KEY.");
		}
		if (propiedades.remitenteCorreo() == null || propiedades.remitenteCorreo().isBlank()) {
			throw new IllegalStateException("BREVO_ACTIVO=true exige BREVO_REMITENTE_CORREO.");
		}
	}

}
