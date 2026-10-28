package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CabecerasSeguridadFilterTest {

	private MockHttpServletResponse ejecutar(String ruta) throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();
		new CabecerasSeguridadFilter().doFilter(new MockHttpServletRequest("GET", ruta), res, new MockFilterChain());
		return res;
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/auth/login", "/api/v1/auth/%6cogin", "/api/v1/admin/reservas",
			"/api/v1/%61dmin/reservas", "/API/V1/ADMIN/reservas", "/api/v1/admin/;x=1", "/api/v1/auth/refresh/",
			"/api/v1//admin/reservas" })
	void noStoreEnAdminYAuthTambienConVariantes(String ruta) throws Exception {
		assertThat(ejecutar(ruta).getHeader("Cache-Control")).isEqualTo("no-store");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/api/v1/config", "/api/v1/disponibilidad" })
	void rutasPublicasSinNoStore(String ruta) throws Exception {
		MockHttpServletResponse res = ejecutar(ruta);
		assertThat(res.getHeader("Cache-Control")).isNull();
		assertThat(res.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
	}

}
