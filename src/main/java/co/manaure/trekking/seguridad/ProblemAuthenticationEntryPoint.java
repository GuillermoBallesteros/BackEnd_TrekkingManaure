package co.manaure.trekking.seguridad;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 401 NO_AUTENTICADO en ProblemDetail (sin token, token inválido o vencido). */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		escribir(response, 401, "Unauthorized", "Inicia sesión para continuar.", "NO_AUTENTICADO");
		response.setHeader("WWW-Authenticate", "Bearer");
	}

	static void escribir(HttpServletResponse response, int status, String title, String detail, String code)
			throws IOException {
		response.setStatus(status);
		response.setContentType("application/problem+json");
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
				+ ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}");
	}

}
