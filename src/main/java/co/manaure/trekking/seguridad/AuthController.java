package co.manaure.trekking.seguridad;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.seguridad.AuthService.ResultadoLogin;
import co.manaure.trekking.seguridad.dto.LoginRequest;
import co.manaure.trekking.seguridad.dto.SesionResponse;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	static final String COOKIE_REFRESH = "refresh_token";
	static final String RUTA_COOKIE = "/api/v1/auth";
	static final String CABECERA_REQUERIDA = "X-Requested-With";

	private final AuthService authService;
	private final boolean cookieSecure;

	public AuthController(AuthService authService, @Value("${app.cookie.secure:true}") boolean cookieSecure) {
		this.authService = authService;
		this.cookieSecure = cookieSecure;
	}

	@PostMapping("/login")
	public ResponseEntity<SesionResponse> login(@RequestBody LoginRequest request, HttpServletRequest http) {
		ResultadoLogin resultado = authService.login(request, http.getRemoteAddr());
		return respuestaConCookie(resultado);
	}

	/** Rota el refresh. Exige la cookie y la cabecera X-Requested-With (defensa CSRF, diseño §10.1). */
	@PostMapping("/refresh")
	public ResponseEntity<?> refresh(
			@CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken,
			@RequestHeader(name = CABECERA_REQUERIDA, required = false) String requestedWith) {
		exigirCabecera(requestedWith);
		try {
			return respuestaConCookie(authService.refrescar(refreshToken));
		}
		catch (SesionInvalidaException ex) {
			ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
			pd.setProperty("code", ex.getCode().name());
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.SET_COOKIE, cookieBorrada().toString()).body(pd);
		}
	}

	/** Cierra la sesión: revoca la familia y borra la cookie. Idempotente. */
	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CookieValue(name = COOKIE_REFRESH, required = false) String refreshToken,
			@RequestHeader(name = CABECERA_REQUERIDA, required = false) String requestedWith) {
		exigirCabecera(requestedWith);
		authService.cerrarSesion(refreshToken);
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookieBorrada().toString()).build();
	}

	private static void exigirCabecera(String valor) {
		if (valor == null || valor.isBlank()) {
			throw new NegocioException(CodigoError.CABECERA_REQUERIDA, HttpStatus.FORBIDDEN,
					"Falta la cabecera X-Requested-With.");
		}
	}

	private ResponseEntity<SesionResponse> respuestaConCookie(ResultadoLogin resultado) {
		ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(COOKIE_REFRESH, resultado.refreshToken())
				.path(RUTA_COOKIE).httpOnly(true).secure(cookieSecure).sameSite("Strict");
		// Con "recordar" la cookie dura lo que resta de la sesión; sin él es de sesión (sin Max-Age).
		resultado.maxAgeCookie().ifPresent(cookie::maxAge);
		return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.build().toString()).body(resultado.sesion());
	}

	private ResponseCookie cookieBorrada() {
		return ResponseCookie.from(COOKIE_REFRESH, "").path(RUTA_COOKIE).httpOnly(true).secure(cookieSecure)
				.sameSite("Strict").maxAge(0).build();
	}

}
