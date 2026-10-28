package co.manaure.trekking.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/** Hallazgo H6: los secretos de ejemplo o débiles impiden el arranque en prod y avisan en dev. */
@ExtendWith(OutputCaptureExtension.class)
class ValidadorSecretosTest {

	private static final String JWT_BUENO = "j".repeat(48);
	private static final String CLAVE_BUENA = "Clave-Larga-12+";
	private static final String SALUD_BUENA = "1:MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

	private ApplicationContextRunner runner(String perfil) {
		return new ApplicationContextRunner().withUserConfiguration(ValidadorSecretos.class)
				.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(perfil))
				.withPropertyValues("app.seguridad.jwt-secret=" + JWT_BUENO, "app.admin.clave=" + CLAVE_BUENA,
						"app.cifrado.salud-clave=" + SALUD_BUENA, "spring.datasource.password=db-secreta-77");
	}

	static Stream<Arguments> invalidos() {
		return Stream.of(
				Arguments.of("app.seguridad.jwt-secret", "x".repeat(60) + "CaMbIaR", "JWT_SECRET"),
				Arguments.of("app.seguridad.jwt-secret",
						"cambiar-por-un-secreto-aleatorio-de-al-menos-48-caracteres", "JWT_SECRET"),
				Arguments.of("app.admin.clave", "cambiar-esta-clave", "ADMIN_PASSWORD"),
				Arguments.of("app.admin.clave", "xx-CAMBIAR-xxxxxxxx", "ADMIN_PASSWORD"),
				Arguments.of("app.cifrado.salud-clave", "1:reemplazar-por-32-bytes-aleatorios-en-base64",
						"HEALTH_ENCRYPTION_KEY"),
				Arguments.of("app.cifrado.salud-clave", "1:cambiarcambiarcambiar", "HEALTH_ENCRYPTION_KEY"),
				Arguments.of("spring.datasource.password", "cambiar-esta-clave", "DB_PASSWORD"),
				Arguments.of("spring.datasource.password", "mi-cambiar-clave", "DB_PASSWORD"));
	}

	@ParameterizedTest
	@MethodSource("invalidos")
	void enProdUnValorDeEjemploImpideArrancarSinMostrarElValor(String propiedad, String valor, String variable) {
		runner("prod").withPropertyValues(propiedad + "=" + valor).run(ctx -> {
			assertThat(ctx).hasFailed();
			assertThat(ctx.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class);
			assertThat(rootMessage(ctx.getStartupFailure())).contains(variable);
			assertThat(rootMessage(ctx.getStartupFailure())).doesNotContain(valor);
		});
	}

	@ParameterizedTest
	@MethodSource("invalidos")
	void enDevSoloAdvierteConAdvertenciaFuerte(String propiedad, String valor, String variable,
			CapturedOutput salida) {
		runner("dev").withPropertyValues(propiedad + "=" + valor).run(ctx -> assertThat(ctx).hasNotFailed());
		assertThat(salida.getOut() + salida.getErr()).contains("ADVERTENCIA DE SEGURIDAD").contains(variable)
				.doesNotContain(valor);
	}

	@Test
	void enProdJwtDe47CaracteresFallaYDe48Pasa() {
		runner("prod").withPropertyValues("app.seguridad.jwt-secret=" + "j".repeat(47)).run(ctx -> {
			assertThat(ctx).hasFailed();
			assertThat(rootMessage(ctx.getStartupFailure())).contains("JWT_SECRET").contains("48");
		});
		runner("prod").run(ctx -> assertThat(ctx).hasNotFailed());
	}

	@Test
	void enProdAdminPasswordDe11FallaYDe12Pasa() {
		runner("prod").withPropertyValues("app.admin.clave=" + "a".repeat(11)).run(ctx -> {
			assertThat(ctx).hasFailed();
			assertThat(rootMessage(ctx.getStartupFailure())).contains("ADMIN_PASSWORD");
		});
		runner("prod").withPropertyValues("app.admin.clave=" + "a".repeat(12)).run(ctx -> assertThat(ctx).hasNotFailed());
	}

	@Test
	void enProdAdminPasswordVaciaFalla() {
		runner("prod").withPropertyValues("app.admin.clave=").run(ctx -> assertThat(ctx).hasFailed());
	}

	@Test
	void enDevLosLargosCortosNoSeExigen() {
		runner("dev").withPropertyValues("app.seguridad.jwt-secret=" + "j".repeat(32), "app.admin.clave=corta-123")
				.run(ctx -> assertThat(ctx).hasNotFailed());
	}

	@Test
	void secretosFuertesArrancanEnProdSinAdvertencias(CapturedOutput salida) {
		runner("prod").run(ctx -> assertThat(ctx).hasNotFailed());
		assertThat(salida.getOut() + salida.getErr()).doesNotContain("ADVERTENCIA DE SEGURIDAD");
	}

	private static String rootMessage(Throwable t) {
		Throwable r = t;
		while (r.getCause() != null) {
			r = r.getCause();
		}
		return r.getMessage();
	}

}
