package co.manaure.trekking.seguridad;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/** JWT HS256 con secreto JWT_SECRET (>= 32 bytes). Si falta o es corto, la app no arranca. */
@Configuration
public class JwtConfig {

	static final int MIN_BYTES = 32;

	private final SecretKey clave;

	public JwtConfig(@Value("${app.seguridad.jwt-secret}") String secreto) {
		if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES) {
			throw new IllegalStateException("JWT_SECRET debe tener al menos " + MIN_BYTES + " bytes.");
		}
		this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder() {
		return new NimbusJwtEncoder(new ImmutableSecret<>(clave));
	}

	@Bean
	JwtDecoder jwtDecoder() {
		return NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
	}

}
