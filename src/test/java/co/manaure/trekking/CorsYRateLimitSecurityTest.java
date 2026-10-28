package co.manaure.trekking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.manaure.trekking.api.PublicoController;
import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.reserva.DisponibilidadService;

/** CORS y rate limit conectados en la cadena real de seguridad (limite bajo para la prueba). */
@Import({ SecurityConfig.class, PublicoController.class })
@TestPropertySource(properties = { "app.cors.origin=http://front.test, http://otro.test",
		"app.rate-limit.registro-por-minuto=2" })
@WebMvcTest(PublicoController.class)
class CorsYRateLimitSecurityTest {

	@MockitoBean
	ConfiguracionService configuracionService;

	@MockitoBean
	DisponibilidadService disponibilidadService;

	@BeforeEach
	void configuracionDePrueba() {
		ConfiguracionEvento cfg = Mockito.mock(ConfiguracionEvento.class);
		Mockito.when(cfg.getFechaEventoInicio()).thenReturn(LocalDate.of(2026, 11, 15));
		Mockito.when(cfg.getFechaEventoFin()).thenReturn(LocalDate.of(2026, 11, 16));
		Mockito.when(configuracionService.obtener()).thenReturn(cfg);
	}

	@MockitoBean
	org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder;

	@Autowired
	MockMvc mvc;

	private static RequestPostProcessor desde(String ip) {
		return r -> {
			r.setRemoteAddr(ip);
			return r;
		};
	}

	@Test
	void preflightPublicoDesdeOrigenPermitido() throws Exception {
		mvc.perform(options("/api/v1/reservas").header("Origin", "http://front.test")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://front.test"))
				.andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,OPTIONS"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
	}

	@Test
	void preflightAdminDesdeOrigenPermitidoConAuthorization() throws Exception {
		mvc.perform(options("/api/v1/admin/reservas").header("Origin", "http://otro.test")
				.header("Access-Control-Request-Method", "PUT")
				.header("Access-Control-Request-Headers", "authorization,content-type"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://otro.test"));
	}

	@Test
	void preflightConXRequestedWithYAuthorizationSoloParaElOrigenPermitido() throws Exception {
		mvc.perform(options("/api/v1/auth/refresh").header("Origin", "http://front.test")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "x-requested-with,authorization"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "http://front.test"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"))
				.andExpect(header().string("Access-Control-Allow-Headers",
						org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsStringIgnoringCase("X-Requested-With"),
								org.hamcrest.Matchers.containsStringIgnoringCase("Authorization"))));
		mvc.perform(options("/api/v1/auth/refresh").header("Origin", "http://malo.test")
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "x-requested-with,authorization"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@Test
	void conCredencialesElOrigenEsExactoYNuncaAsterisco() throws Exception {
		mvc.perform(get("/api/v1/config").header("Origin", "http://front.test"))
				.andExpect(header().string("Access-Control-Allow-Origin", "http://front.test"))
				.andExpect(header().string("Access-Control-Allow-Credentials", "true"));
		mvc.perform(get("/api/v1/config").header("Origin", "http://malo.test"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@Test
	void cabecerasDeSeguridadEnTodasLasRespuestasYNoStoreSoloEnAdminYAuth() throws Exception {
		for (var peticion : java.util.List.of(get("/api/v1/config"), get("/api/v1/admin/reservas"),
				post("/api/v1/auth/refresh"), get("/api/v1/otra-cosa"))) {
			mvc.perform(peticion)
					.andExpect(header().string("X-Content-Type-Options", "nosniff"))
					.andExpect(header().string("Referrer-Policy", "no-referrer"))
					.andExpect(header().string("X-Frame-Options", "DENY"))
					.andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
		}
		mvc.perform(get("/api/v1/admin/reservas")).andExpect(status().isUnauthorized())
				.andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(post("/api/v1/auth/refresh")).andExpect(header().string("Cache-Control", "no-store"));
		mvc.perform(get("/api/v1/config")).andExpect(header().doesNotExist("Cache-Control"));
	}

	@Test
	void preflightDesdeOrigenNoPermitidoNoRecibeCabeceras() throws Exception {
		mvc.perform(options("/api/v1/reservas").header("Origin", "http://malo.test")
				.header("Access-Control-Request-Method", "POST"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
		mvc.perform(options("/api/v1/admin/reservas").header("Origin", "http://malo.test")
				.header("Access-Control-Request-Method", "PUT"))
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void peticionRealConOrigenPermitidoYNoPermitido() throws Exception {
		mvc.perform(get("/api/v1/config").header("Origin", "http://front.test"))
				.andExpect(header().string("Access-Control-Allow-Origin", "http://front.test"));
		mvc.perform(get("/api/v1/config").header("Origin", "http://malo.test"))
				.andExpect(status().isForbidden())
				.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}

	@Test
	void exponeLocationContentDispositionYRetryAfter() throws Exception {
		mvc.perform(get("/api/v1/config").header("Origin", "http://front.test"))
				.andExpect(header().string("Access-Control-Expose-Headers",
						"Location, Content-Disposition, Retry-After"));
	}

	@Test
	void arranqueFallaConCorsVacioOConAsterisco() {
		assertThatThrownBy(() -> CorsConfig.construirCors("")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> CorsConfig.construirCors("  , ")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> CorsConfig.construirCors(null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> CorsConfig.construirCors("*")).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> CorsConfig.construirCors("http://a.test,*"))
				.isInstanceOf(IllegalStateException.class);
		assertThat(CorsConfig.construirCors("http://a.test")).isNotNull();
	}

	@Test
	void laAplicacionNoArrancaConCorsOriginVacioOConAsterisco() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
				.withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations
						.of(org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration.class))
				.withUserConfiguration(CorsConfig.class);
		for (String valor : new String[] { "", "  ", "*", "http://a.test,*", "https://*.dominio.com" }) {
			runner.withPropertyValues("app.cors.origin=" + valor).run(contexto -> {
				assertThat(contexto).hasFailed();
				assertThat(contexto.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
						.rootCause().hasMessageContaining("CORS_ORIGIN");
			});
		}
		runner.run(contexto -> assertThat(contexto).hasFailed());
		runner.withPropertyValues("app.cors.origin=http://a.test")
				.run(contexto -> assertThat(contexto).hasNotFailed());
	}

	@Test
	void limiteSeAplicaAntesDeLaAutenticacionYNoAfectaOtrasRutas() throws Exception {
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/v1/reservas").with(desde("5.5.5.5")))
					.andExpect(status().is(org.hamcrest.Matchers.not(429)));
		}
		mvc.perform(post("/api/v1/reservas").with(desde("5.5.5.5")))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("DEMASIADAS_SOLICITUDES"));
		mvc.perform(post("/api/v1/reservas").with(desde("6.6.6.6")))
				.andExpect(status().is(org.hamcrest.Matchers.not(429)));
		for (int i = 0; i < 5; i++) {
			mvc.perform(get("/api/v1/config").with(desde("5.5.5.5"))).andExpect(status().isOk());
			mvc.perform(get("/api/v1/admin/reservas").with(desde("5.5.5.5")))
					.andExpect(status().isUnauthorized());
		}
	}

}
