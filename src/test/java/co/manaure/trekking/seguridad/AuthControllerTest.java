package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.manaure.trekking.SecurityConfig;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.GlobalExceptionHandler;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.seguridad.AuthService.ResultadoLogin;
import co.manaure.trekking.seguridad.dto.SesionResponse;

@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
@WebMvcTest(AuthController.class)
class AuthControllerTest {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	AuthService authService;

	@MockitoBean
	JwtDecoder jwtDecoder;

	private static final String CUERPO = "{\"correo\":\"admin@ejemplo.com\",\"clave\":\"clave-correcta-1\",\"recordar\":true}";

	private static ResultadoLogin resultado(boolean recordada) {
		SesionResponse s = new SesionResponse("jwt.de.prueba", "Bearer", "admin@ejemplo.com", "Admin",
				1793200000000L, 1793200900000L, 1793804800000L, recordada);
		return new ResultadoLogin(s, "token-refresh-en-claro",
				recordada ? Optional.of(Duration.ofDays(7)) : Optional.empty());
	}

	@Test
	void loginEsPublicoYDevuelveLaSesionConTodosSusCampos() throws Exception {
		when(authService.login(any(), any())).thenReturn(resultado(true));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("jwt.de.prueba"))
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.correo").value("admin@ejemplo.com"))
				.andExpect(jsonPath("$.nombre").value("Admin"))
				.andExpect(jsonPath("$.iniciadaEn").value(1793200000000L))
				.andExpect(jsonPath("$.expiraEn").value(1793200900000L))
				.andExpect(jsonPath("$.sesionExpiraEn").value(1793804800000L))
				.andExpect(jsonPath("$.recordada").value(true));
	}

	@Test
	void conRecordarLaCookieLlevaLosAtributosDelDiseno() throws Exception {
		when(authService.login(any(), any())).thenReturn(resultado(true));

		String cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(cookie).startsWith("refresh_token=token-refresh-en-claro;")
				.contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure")
				.contains("SameSite=Strict").contains("Max-Age=604800");
	}

	@Test
	void sinRecordarLaCookieEsDeSesionSinMaxAge() throws Exception {
		when(authService.login(any(), any())).thenReturn(resultado(false));

		String cookie = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"admin@ejemplo.com\",\"clave\":\"clave-correcta-1\",\"recordar\":false}"))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(cookie).contains("Path=/api/v1/auth").contains("HttpOnly").contains("Secure")
				.contains("SameSite=Strict").doesNotContain("Max-Age").doesNotContain("Expires");
	}

	@Test
	void credencialesInvalidasDan401() throws Exception {
		when(authService.login(any(), any())).thenThrow(new NegocioException(CodigoError.CREDENCIALES_INVALIDAS,
				HttpStatus.UNAUTHORIZED, "Correo o contraseña incorrectos."));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
				.andExpect(status().isUnauthorized())
				.andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
				.andExpect(jsonPath("$.code").value("CREDENCIALES_INVALIDAS"))
				.andExpect(jsonPath("$.detail").value("Correo o contraseña incorrectos."));
	}

	@Test
	void bloqueoDa429ConRetryAfter() throws Exception {
		when(authService.login(any(), any())).thenThrow(new IntentosExcedidosException(900));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "900"))
				.andExpect(jsonPath("$.code").value("INTENTOS_EXCEDIDOS"));
	}

	@Test
	void refreshConCookieYCabeceraDevuelveLaSesionYLaCookieNueva() throws Exception {
		when(authService.refrescar("token-viejo")).thenReturn(resultado(true));

		String cookie = mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
				.cookie(new jakarta.servlet.http.Cookie("refresh_token", "token-viejo")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").value("jwt.de.prueba"))
				.andExpect(header().string("Cache-Control", "no-store"))
				.andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

		assertThat(cookie).startsWith("refresh_token=token-refresh-en-claro;").contains("HttpOnly")
				.contains("SameSite=Strict").contains("Path=/api/v1/auth");
	}

	@Test
	void refreshYLogoutSinCabeceraDan403CabeceraRequeridaSinTocarElServicio() throws Exception {
		for (String ruta : new String[] { "/api/v1/auth/refresh", "/api/v1/auth/logout" }) {
			mvc.perform(post(ruta).cookie(new jakarta.servlet.http.Cookie("refresh_token", "x")))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("CABECERA_REQUERIDA"));
			mvc.perform(post(ruta).header("X-Requested-With", " ").cookie(new jakarta.servlet.http.Cookie("refresh_token", "x")))
					.andExpect(status().isForbidden());
		}
		org.mockito.Mockito.verifyNoInteractions(authService);
	}

	@Test
	void refreshInvalidoDa401SesionInvalidaYBorraLaCookie() throws Exception {
		when(authService.refrescar(any())).thenThrow(new SesionInvalidaException());

		mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESION_INVALIDA"))
				.andExpect(jsonPath("$.detail").value("Tu sesión terminó. Inicia sesión de nuevo."))
				.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.allOf(
						org.hamcrest.Matchers.containsString("refresh_token=;"),
						org.hamcrest.Matchers.containsString("Max-Age=0"),
						org.hamcrest.Matchers.containsString("Path=/api/v1/auth"))));
	}

	@Test
	void logoutDa204BorraLaCookieYEsIdempotenteSinCookie() throws Exception {
		mvc.perform(post("/api/v1/auth/logout").header("X-Requested-With", "XMLHttpRequest"))
				.andExpect(status().isNoContent())
				.andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
		mvc.perform(post("/api/v1/auth/logout").header("X-Requested-With", "XMLHttpRequest")
				.cookie(new jakarta.servlet.http.Cookie("refresh_token", "t")))
				.andExpect(status().isNoContent());
		org.mockito.Mockito.verify(authService).cerrarSesion("t");
	}

	@Test
	void datosInvalidosDan400ConCamposYTextosDelFront() throws Exception {
		when(authService.login(any(), any())).thenThrow(new NegocioException(CodigoError.DATOS_INVALIDOS,
				HttpStatus.BAD_REQUEST, "Revisa los datos marcados.", Map.of("campos", Map.of("correo", "Escribe tu correo."))));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.detail").value("Revisa los datos marcados."))
				.andExpect(jsonPath("$.campos.correo").value("Escribe tu correo."));
	}

}
