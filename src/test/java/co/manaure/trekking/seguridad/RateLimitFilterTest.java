package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

	static class RelojMutable extends Clock {
		Instant ahora = Instant.parse("2026-01-01T10:00:10Z");
		@Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
		@Override public Clock withZone(java.time.ZoneId zone) { return this; }
		@Override public Instant instant() { return ahora; }
	}

	static final String RESERVAS = "/api/v1/reservas";
	static final String LOGIN = "/api/v1/auth/login";

	RelojMutable reloj = new RelojMutable();
	RateLimitFilter filtro = new RateLimitFilter(reloj, 10, 20, 60);

	private MockHttpServletResponse peticion(String metodo, String ruta, String ip) throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
		req.setRemoteAddr(ip);
		MockHttpServletResponse res = new MockHttpServletResponse();
		filtro.doFilter(req, res, new MockFilterChain());
		return res;
	}

	@Test
	void registro11DaTooManyRequests() throws Exception {
		for (int i = 0; i < 10; i++) {
			assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
		}
		MockHttpServletResponse r = peticion("POST", RESERVAS, "1.1.1.1");
		assertThat(r.getStatus()).isEqualTo(429);
		assertThat(r.getHeader("Retry-After")).isEqualTo("50");
		assertThat(r.getContentAsString()).contains("\"code\":\"DEMASIADAS_SOLICITUDES\"");
		assertThat(r.getContentType()).startsWith("application/problem+json");
		assertThat(r.getContentAsString()).doesNotContain("1.1.1.1");
	}

	@Test
	void ipsDistintasNoSeAfectan() throws Exception {
		for (int i = 0; i < 11; i++) {
			peticion("POST", RESERVAS, "1.1.1.1");
		}
		assertThat(peticion("POST", RESERVAS, "2.2.2.2").getStatus()).isEqualTo(200);
	}

	@Test
	void laVentanaSeReiniciaAlAvanzarElReloj() throws Exception {
		for (int i = 0; i < 11; i++) {
			peticion("POST", RESERVAS, "1.1.1.1");
		}
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(429);
		reloj.ahora = Instant.parse("2026-01-01T10:01:00Z");
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
	}

	@Test
	void getYRutasAdminNoSeLimitan() throws Exception {
		for (int i = 0; i < 100; i++) {
			assertThat(peticion("GET", "/api/v1/config", "1.1.1.1").getStatus()).isEqualTo(200);
			assertThat(peticion("GET", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
			assertThat(peticion("GET", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
			assertThat(peticion("POST", "/api/v1/admin/reservas/PER-0001/pagos", "1.1.1.1").getStatus()).isEqualTo(200);
		}
		assertThat(filtro.tamano()).isZero();
	}

	@Test
	void login21DaTooManyRequestsEnVentanaDeQuinceMinutos() throws Exception {
		for (int i = 0; i < 20; i++) {
			assertThat(peticion("POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
		}
		MockHttpServletResponse r = peticion("POST", LOGIN, "1.1.1.1");
		assertThat(r.getStatus()).isEqualTo(429);
		assertThat(r.getContentAsString()).contains("\"code\":\"DEMASIADAS_SOLICITUDES\"");
		// Pertenece a la ventana de 15 minutos [10:00:00, 10:15:00): quedan 890 s.
		assertThat(r.getHeader("Retry-After")).isEqualTo("890");
		assertThat(peticion("POST", LOGIN, "2.2.2.2").getStatus()).isEqualTo(200);
		// El límite de login es independiente del de reservas.
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);

		reloj.ahora = Instant.parse("2026-01-01T10:15:00Z");
		assertThat(peticion("POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
	}

	@Test
	void refresh61DaTooManyRequestsEnVentanaDeUnMinutoYEsIndependiente() throws Exception {
		for (int i = 0; i < 60; i++) {
			assertThat(peticion("POST", "/api/v1/auth/refresh", "1.1.1.1").getStatus()).isEqualTo(200);
		}
		MockHttpServletResponse r = peticion("POST", "/api/v1/auth/refresh", "1.1.1.1");
		assertThat(r.getStatus()).isEqualTo(429);
		assertThat(r.getHeader("Retry-After")).isEqualTo("50");
		assertThat(r.getContentAsString()).contains("\"code\":\"DEMASIADAS_SOLICITUDES\"");
		assertThat(peticion("POST", "/api/v1/auth/refresh", "2.2.2.2").getStatus()).isEqualTo(200);
		assertThat(peticion("POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
		reloj.ahora = Instant.parse("2026-01-01T10:01:00Z");
		assertThat(peticion("POST", "/api/v1/auth/refresh", "1.1.1.1").getStatus()).isEqualTo(200);
	}

	@Test
	void limpiaEntradasVencidas() throws Exception {
		peticion("POST", RESERVAS, "1.1.1.1");
		peticion("POST", RESERVAS, "2.2.2.2");
		peticion("POST", RESERVAS, "3.3.3.3");
		assertThat(filtro.tamano()).isEqualTo(3);
		reloj.ahora = Instant.parse("2026-01-01T10:05:00Z");
		peticion("POST", RESERVAS, "9.9.9.9");
		assertThat(filtro.tamano()).isEqualTo(1);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "/api/v1/auth/%6cogin", "/api/v1/auth/login/",
			"/api/v1/auth/login;jsessionid=abc", "/API/V1/AUTH/LOGIN", "/api/v1/auth//login", "/api/v1/auth/./login",
			"/api/v1/auth/x/../login", "/api/v1/auth/%4Cogin" })
	void variantesDeLoginComparteElContadorDeLaRutaNormal(String variante) throws Exception {
		for (int i = 0; i < 20; i++) {
			assertThat(peticion("POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(200);
		}
		assertThat(peticion("POST", variante, "1.1.1.1").getStatus()).isEqualTo(429);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "/api/v1/auth/refres%68", "/api/v1/auth/refresh/",
			"/api/v1/auth/refresh;x=1", "/API/V1/AUTH/REFRESH", "/api/v1/auth/logout", "/api/v1/auth/otra" })
	void restoDeAuthComparteLaReglaDeRefresh(String variante) throws Exception {
		for (int i = 0; i < 60; i++) {
			assertThat(peticion("POST", "/api/v1/auth/refresh", "1.1.1.1").getStatus()).isEqualTo(200);
		}
		assertThat(peticion("POST", variante, "1.1.1.1").getStatus()).isEqualTo(429);
	}

	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(strings = { "/api/v1/reserva%73", "/api/v1/reservas/",
			"/api/v1/reservas;jsessionid=1", "/API/V1/RESERVAS", "/api/v1//reservas" })
	void variantesDeReservasComparteElContador(String variante) throws Exception {
		for (int i = 0; i < 10; i++) {
			assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
		}
		assertThat(peticion("POST", variante, "1.1.1.1").getStatus()).isEqualTo(429);
	}

	@Test
	void tambienCuentaLaVarianteComoIntentoDeLaRutaNormal() throws Exception {
		for (int i = 0; i < 20; i++) {
			assertThat(peticion("POST", "/api/v1/auth/%6cogin", "1.1.1.1").getStatus()).isEqualTo(200);
		}
		assertThat(peticion("POST", LOGIN, "1.1.1.1").getStatus()).isEqualTo(429);
	}

	@Test
	void rutasAjenasNoSeLimitan() throws Exception {
		for (int i = 0; i < 100; i++) {
			assertThat(peticion("POST", "/api/v1/authx/login", "1.1.1.1").getStatus()).isEqualTo(200);
		}
	}

}
