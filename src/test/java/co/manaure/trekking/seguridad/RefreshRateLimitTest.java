package co.manaure.trekking.seguridad;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.manaure.trekking.SecurityConfig;
import co.manaure.trekking.comun.error.GlobalExceptionHandler;

/** HU-N06: el exceso de refresh por IP (60 por minuto) da 429 DEMASIADAS_SOLICITUDES. */
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
@TestPropertySource(properties = "app.rate-limit.refresh-por-minuto=60")
@WebMvcTest(AuthController.class)
class RefreshRateLimitTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AuthService authService;

	@MockitoBean
	JwtDecoder jwtDecoder;

	private static RequestPostProcessor desde(String ip) {
		return r -> {
			r.setRemoteAddr(ip);
			return r;
		};
	}

	private ResultActions refresh(String ip) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest").with(desde(ip)));
	}

	@Test
	void elRefreshSesentayUnoDeLaMismaIpDa429YOtraIpNoSeAfecta() throws Exception {
		when(authService.refrescar(any())).thenThrow(new SesionInvalidaException());

		for (int i = 1; i <= 60; i++) {
			refresh("9.9.9.9").andExpect(status().isUnauthorized());
		}
		refresh("9.9.9.9").andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("DEMASIADAS_SOLICITUDES"));
		refresh("10.10.10.10").andExpect(status().isUnauthorized());
	}

}
