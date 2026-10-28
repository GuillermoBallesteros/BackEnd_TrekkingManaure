package co.manaure.trekking.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Rechaza secretos de ejemplo (hallazgo H6). Revisa JWT_SECRET, ADMIN_PASSWORD, HEALTH_ENCRYPTION_KEY y DB_PASSWORD:
 * no pueden contener "cambiar" (sin distinguir mayúsculas) ni coincidir con los valores de ejemplo históricos del
 * {@code .env.example}. En prod además exige JWT_SECRET de al menos 48 caracteres y ADMIN_PASSWORD de al menos 12 (y
 * presente). En prod un problema impide el arranque; en otros perfiles se registra una advertencia fuerte. Los
 * mensajes nombran la variable, nunca su valor.
 */
@Component
public class ValidadorSecretos {

	private static final Logger log = LoggerFactory.getLogger(ValidadorSecretos.class);

	static final int JWT_MIN_PROD = 48;
	static final int ADMIN_CLAVE_MIN_PROD = 12;

	/** Valores que traía el {@code .env.example} anterior. */
	static final Set<String> VALORES_DE_EJEMPLO = Set.of("cambiar-esta-clave",
			"cambiar-por-un-secreto-aleatorio-de-al-menos-48-caracteres",
			"1:reemplazar-por-32-bytes-aleatorios-en-base64");

	public ValidadorSecretos(Environment entorno) {
		boolean prod = entorno.acceptsProfiles(Profiles.of("prod"));
		Map<String, String> secretos = new java.util.LinkedHashMap<>();
		secretos.put("JWT_SECRET", entorno.getProperty("app.seguridad.jwt-secret"));
		secretos.put("ADMIN_PASSWORD", entorno.getProperty("app.admin.clave"));
		secretos.put("HEALTH_ENCRYPTION_KEY", entorno.getProperty("app.cifrado.salud-clave"));
		secretos.put("DB_PASSWORD", leer(entorno, "spring.datasource.password"));
		List<String> problemas = problemas(secretos, prod);
		if (problemas.isEmpty()) {
			return;
		}
		String texto = String.join(" ", problemas);
		if (prod) {
			throw new IllegalStateException("Configuración de secretos insegura: " + texto);
		}
		log.warn("ADVERTENCIA DE SEGURIDAD: {} En producción la aplicación no arrancará así.", texto);
	}

	/** Un marcador sin resolver (p. ej. datasource suministrado por Testcontainers) cuenta como ausente. */
	private static String leer(Environment entorno, String propiedad) {
		try {
			return entorno.getProperty(propiedad);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

	/** Problemas encontrados (vacío si no hay). Nunca incluye el valor de un secreto. */
	static List<String> problemas(Map<String, String> secretos, boolean prod) {
		List<String> problemas = new ArrayList<>();
		secretos.forEach((variable, valor) -> {
			if (valor != null && !valor.isBlank()) {
				if (valor.toLowerCase(Locale.ROOT).contains("cambiar") || VALORES_DE_EJEMPLO.contains(valor.strip())) {
					problemas.add(variable + " tiene un valor de ejemplo: genera uno aleatorio.");
				}
			}
		});
		if (prod) {
			String jwt = secretos.get("JWT_SECRET");
			if (jwt != null && !jwt.isBlank() && jwt.length() < JWT_MIN_PROD) {
				problemas.add("JWT_SECRET debe tener al menos " + JWT_MIN_PROD + " caracteres.");
			}
			String clave = secretos.get("ADMIN_PASSWORD");
			if (clave == null || clave.length() < ADMIN_CLAVE_MIN_PROD) {
				problemas.add("ADMIN_PASSWORD es obligatoria y debe tener al menos " + ADMIN_CLAVE_MIN_PROD
						+ " caracteres.");
			}
		}
		return problemas;
	}

}
