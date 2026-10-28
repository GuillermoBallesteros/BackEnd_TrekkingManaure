package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/** HU-N05: JWT_SECRET ausente o de menos de 32 bytes impide el arranque. */
class JwtSecretArranqueTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(JwtConfig.class);

	@Test
	void sinJwtSecretLaAplicacionNoArranca() {
		runner.run(contexto -> assertThat(contexto).hasFailed());
	}

	@Test
	void conJwtSecretCortoLaAplicacionNoArranca() {
		runner.withPropertyValues("app.seguridad.jwt-secret=" + "x".repeat(31)).run(contexto -> {
			assertThat(contexto).hasFailed();
			assertThat(contexto.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
		});
	}

	@Test
	void conJwtSecretDeAlMenos32BytesArranca() {
		runner.withPropertyValues("app.seguridad.jwt-secret=" + "x".repeat(32))
				.run(contexto -> assertThat(contexto).hasNotFailed().hasSingleBean(JwtDecoder.class));
	}

}
