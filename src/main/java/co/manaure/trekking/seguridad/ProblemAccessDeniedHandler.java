package co.manaure.trekking.seguridad;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 403 ACCESO_DENEGADO en ProblemDetail. */
public class ProblemAccessDeniedHandler implements AccessDeniedHandler {

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ProblemAuthenticationEntryPoint.escribir(response, 403, "Forbidden",
				"No tienes permiso para esta acción.", "ACCESO_DENEGADO");
	}

}
