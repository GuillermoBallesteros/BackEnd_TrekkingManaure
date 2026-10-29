package co.manaure.trekking.notificacion;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envío transaccional con plantilla: {@code POST /smtp/email} de la API v3 de Brevo (diseño §18). 429 y 5xx, y la
 * falta de respuesta, se reintentan hasta {@link #MAX_INTENTOS} veces con espera creciente; cualquier otro error
 * falla de inmediato.
 */
public class ClienteBrevo {

	static final int MAX_INTENTOS = 3;
	static final String ETIQUETA = "trekking-perija";

	private final RestClient http;
	private final PropiedadesBrevo propiedades;

	public ClienteBrevo(RestClient http, PropiedadesBrevo propiedades) {
		this.http = http;
		this.propiedades = propiedades;
	}

	/** @return el {@code messageId} que asigna Brevo (vacío si no lo devuelve) */
	public String enviar(Destinatario para, long plantillaId, Map<String, Object> parametros, TipoAviso tipo) {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		cuerpo.put("sender", Map.of("email", propiedades.remitenteCorreo(), "name", propiedades.remitenteNombre()));
		cuerpo.put("to", List.of(Map.of("email", para.correo(), "name", para.nombre())));
		cuerpo.put("templateId", plantillaId);
		cuerpo.put("params", parametros);
		cuerpo.put("tags", List.of(ETIQUETA, tipo.name()));

		for (int intento = 1;; intento++) {
			try {
				Map<?, ?> respuesta = http.post().uri("/smtp/email").contentType(MediaType.APPLICATION_JSON)
						.accept(MediaType.APPLICATION_JSON).header("api-key", propiedades.apiKey()).body(cuerpo)
						.retrieve().body(Map.class);
				Object mensaje = respuesta == null ? null : respuesta.get("messageId");
				return mensaje == null ? "" : mensaje.toString();
			}
			catch (RestClientResponseException ex) {
				int estado = ex.getStatusCode().value();
				boolean temporal = estado == 429 || estado >= 500;
				if (!temporal || intento >= MAX_INTENTOS) {
					throw new ErrorBrevo(estado, ex);
				}
			}
			catch (ResourceAccessException ex) {
				if (intento >= MAX_INTENTOS) {
					throw new ErrorBrevo(0, ex);
				}
			}
			esperar(intento);
		}
	}

	private void esperar(int intento) {
		long espera = propiedades.esperaReintentoMs() * intento;
		if (espera <= 0) {
			return;
		}
		try {
			Thread.sleep(espera);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ErrorBrevo(0, ex);
		}
	}

}
