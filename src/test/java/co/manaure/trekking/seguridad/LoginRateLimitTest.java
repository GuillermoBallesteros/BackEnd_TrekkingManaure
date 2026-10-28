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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.manaure.trekking.SecurityConfig;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.GlobalExceptionHandler;
import co.manaure.trekking.comun.error.NegocioException;

/** HU-N05: más de 20 intentos de login en 15 minutos desde una IP dan 429 DEMASIADAS_SOLICITUDES. */
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
@TestPropertySource(properties = "app.rate-limit.login-por-15-minutos=20")
@WebMvcTest(AuthController.class)
class LoginRateLimitTest {

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

	private org.springframework.test.web.servlet.ResultActions login(String ip) throws Exception {
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"a@b.co\",\"clave\":\"clave-correcta-1\"}").with(desde(ip)));
	}

	@Test
	void elIntentoVeintiunoDeLaMismaIpDa429YOtraIpNoSeAfecta() throws Exception {
		when(authService.login(any(), any())).thenThrow(new NegocioException(CodigoError.CREDENCIALES_INVALIDAS,
				HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));

		for (int i = 1; i <= 20; i++) {
			login("7.7.7.7").andExpect(status().isUnauthorized());
		}
		login("7.7.7.7").andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("DEMASIADAS_SOLICITUDES"));
		login("8.8.8.8").andExpect(status().isUnauthorized());
	}

}
