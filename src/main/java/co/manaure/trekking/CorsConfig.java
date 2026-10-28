package co.manaure.trekking;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** CORS con credenciales (diseño §10.3): orígenes exactos de CORS_ORIGIN; nunca comodín. */
@Configuration
public class CorsConfig {

	/** Orígenes de CORS_ORIGIN (lista separada por comas). Falla el arranque si está vacío o contiene "*". */
	@Bean
	CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origin}") String origenes) {
		return construirCors(origenes);
	}

	static CorsConfigurationSource construirCors(String origenes) {
		List<String> lista = origenes == null ? List.of()
				: Arrays.stream(origenes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
		if (lista.isEmpty()) {
			throw new IllegalStateException("CORS_ORIGIN es obligatorio y no puede estar vacío.");
		}
		if (lista.stream().anyMatch(s -> s.contains("*"))) {
			throw new IllegalStateException("CORS_ORIGIN no puede contener '*'.");
		}
		CorsConfiguration cfg = new CorsConfiguration();
		cfg.setAllowedOrigins(lista);
		cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
		cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
		cfg.setExposedHeaders(List.of("Location", "Content-Disposition", "Retry-After"));
		cfg.setAllowCredentials(true);
		cfg.setMaxAge(3600L);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}

}
