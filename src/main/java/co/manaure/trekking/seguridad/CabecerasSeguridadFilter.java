package co.manaure.trekking.seguridad;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cabeceras de seguridad en todas las respuestas (diseño §10.3): nosniff, Referrer-Policy, X-Frame-Options, CSP
 * restrictiva para una API y {@code Cache-Control: no-store} en /api/v1/admin/** y /api/v1/auth/**.
 * Va al inicio de la cadena para alcanzar también las respuestas de 401, 403, 429 y CORS rechazado.
 * El Swagger UI (solo perfil dev) queda sin CSP porque necesita cargar scripts propios.
 */
public class CabecerasSeguridadFilter extends OncePerRequestFilter {

	static final String CSP = "default-src 'none'; frame-ancestors 'none'";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String ruta = RutaSolicitud.normalizada(request);
		response.setHeader("X-Content-Type-Options", "nosniff");
		response.setHeader("Referrer-Policy", "no-referrer");
		response.setHeader("X-Frame-Options", "DENY");
		if (!ruta.startsWith("/swagger-ui")) {
			response.setHeader("Content-Security-Policy", CSP);
		}
		if (ruta.startsWith("/api/v1/admin/") || ruta.equals("/api/v1/admin") || ruta.startsWith("/api/v1/auth/")
				|| ruta.equals("/api/v1/auth")) {
			response.setHeader("Cache-Control", "no-store");
		}
		chain.doFilter(request, response);
	}

}
