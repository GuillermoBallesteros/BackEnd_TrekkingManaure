package co.manaure.trekking.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Emite el access token: JWT HS256 con sub = id del administrador, email, roles y jti (diseño §10.1). */
@Service
public class JwtService {

	public record AccessToken(String token, Instant expiraEn) {
	}

	private final JwtEncoder encoder;
	private final Duration duracion;

	public JwtService(JwtEncoder encoder, @Value("${app.seguridad.access-minutos:15}") long accesoMinutos) {
		this.encoder = encoder;
		this.duracion = Duration.ofMinutes(accesoMinutos);
	}

	public AccessToken emitir(long administradorId, String correo, Instant ahora) {
		Instant expira = ahora.plus(duracion);
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(Long.toString(administradorId))
				.claim("email", correo)
				.claim("roles", List.of("ADMIN"))
				.id(UUID.randomUUID().toString())
				.issuedAt(ahora)
				.expiresAt(expira)
				.build();
		String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
		return new AccessToken(token, expira);
	}

}
