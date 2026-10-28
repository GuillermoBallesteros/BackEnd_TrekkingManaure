package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.manaure.trekking.SecurityConfig;
import co.manaure.trekking.comun.error.GlobalExceptionHandler;
import co.manaure.trekking.seguridad.AuthService.ResultadoLogin;
import co.manaure.trekking.seguridad.dto.SesionResponse;

/** COOKIE_SECURE=false (desarrollo sobre http://localhost): se omite solo el atributo Secure. */
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
@TestPropertySource(properties = "app.cookie.secure=false")
@WebMvcTest(AuthController.class)
class CookieNoSeguraTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AuthService authService;

	@MockitoBean
	JwtDecoder jwtDecoder;

	@Test
	void conCookieSecureFalseNoSeEmiteSecure() throws Exception {
		SesionResponse s = new SesionResponse("t", "Bearer", "a@b.co", "A", 1L, 2L, 3L, true);
		when(authService.login(any(), any())).thenReturn(new ResultadoLogin(s, "refresh", Optional.of(Duration.ofDays(7))));

		String cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"a@b.co\",\"clave\":\"clave-correcta-1\",\"recordar\":true}"))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(cookie).contains("HttpOnly").contains("SameSite=Strict").contains("Max-Age=604800")
				.doesNotContain("Secure");
	}

}
