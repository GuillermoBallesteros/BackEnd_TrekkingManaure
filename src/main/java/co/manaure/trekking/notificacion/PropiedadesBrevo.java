package co.manaure.trekking.notificacion;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades {@code app.brevo.*} (diseño §13 y §18). {@code plantillas} asocia la clave de cada {@link TipoAviso}
 * con el id de su plantilla en Brevo; un id 0 o ausente apaga ese tipo.
 */
@ConfigurationProperties("app.brevo")
public record PropiedadesBrevo(boolean activo, String apiKey, String url, String remitenteCorreo,
		String remitenteNombre, int lote, long esperaReintentoMs, boolean recuperarAlArrancar,
		Map<String, Long> plantillas) {

	public long plantilla(TipoAviso tipo) {
		Long id = plantillas == null ? null : plantillas.get(tipo.clave());
		return id == null ? 0 : id;
	}

}
