package co.manaure.trekking.seguridad;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.filter.OncePerRequestFilter;

import co.manaure.trekking.comun.error.CodigoError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite de frecuencia por IP (ventana fija, en memoria), diseño §10.2 [SUPUESTO]: {@code POST /reservas} por
 * minuto y {@code POST /auth/login} por ventana de 15 minutos. Responde 429 DEMASIADAS_SOLICITUDES con
 * Retry-After. No registra datos de la solicitud. {@code POST /auth/refresh}: ventana de un minuto (HU-N06).
 */
public class RateLimitFilter extends OncePerRequestFilter {

	static final String RUTA_REGISTRO = "/api/v1/reservas";
	static final String RUTA_LOGIN = "/api/v1/auth/login";
	static final String RUTA_REFRESH = "/api/v1/auth/refresh";
	private static final String RUTA_AUTH = "/api/v1/auth";
	private static final String PREFIJO_AUTH = "/api/v1/auth/";
	private static final long VENTANA_REGISTRO_MS = 60_000L;
	private static final long VENTANA_LOGIN_MS = 15 * 60_000L;

	private record Ventana(long id, long finMs, int cuenta) {
	}

	private record Regla(String grupo, int limite, long ventanaMs) {
	}

	private final Clock clock;
	private final Regla reglaRegistro;
	private final Regla reglaLogin;
	private final Regla reglaRefresh;
	private final Map<String, Ventana> contadores = new ConcurrentHashMap<>();

	private final TopeReservasPorIp topeDiario;

	public RateLimitFilter(Clock clock, int limiteRegistro, int limiteLogin, int limiteRefresh) {
		this(clock, limiteRegistro, limiteLogin, limiteRefresh, null);
	}

	/** {@code topeDiario} (nulo = sin tope) limita además {@code POST /reservas} por IP en 24 horas (H4). */
	public RateLimitFilter(Clock clock, int limiteRegistro, int limiteLogin, int limiteRefresh,
			TopeReservasPorIp topeDiario) {
		this.topeDiario = topeDiario;
		this.clock = clock;
		this.reglaRegistro = new Regla("registro", limiteRegistro, VENTANA_REGISTRO_MS);
		this.reglaLogin = new Regla("login", limiteLogin, VENTANA_LOGIN_MS);
		this.reglaRefresh = new Regla("auth", limiteRefresh, VENTANA_REGISTRO_MS);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Regla regla = reglaDe(request);
		if (regla == null) {
			chain.doFilter(request, response);
			return;
		}
		if (regla == reglaRegistro && topeDiario != null) {
			// Cuenta toda solicitud a la ruta, también las que luego fallan por validación o por el límite por minuto.
			TopeReservasPorIp.Decision d = topeDiario.registrar(request.getRemoteAddr());
			if (d.rechazada()) {
				rechazar(response, d.retryAfterSegundos());
				return;
			}
		}
		long ahora = clock.millis();
		limpiar(ahora);
		long ventana = ahora / regla.ventanaMs();
		long fin = (ventana + 1) * regla.ventanaMs();

		String clave = request.getRemoteAddr() + "|" + regla.grupo();
		Ventana actual = contadores.merge(clave, new Ventana(ventana, fin, 1),
				(v, nueva) -> v.id() == nueva.id() ? new Ventana(v.id(), v.finMs(), v.cuenta() + 1) : nueva);
		if (actual.cuenta() > regla.limite()) {
			long segundos = Math.max(1, (long) Math.ceil((fin - ahora) / 1000.0));
			rechazar(response, segundos);
			return;
		}
		chain.doFilter(request, response);
	}

	private static void rechazar(HttpServletResponse response, long segundos) throws IOException {
		response.setHeader("Retry-After", Long.toString(segundos));
		ProblemAuthenticationEntryPoint.escribir(response, 429, "Too Many Requests",
				"Demasiadas solicitudes. Intenta de nuevo en un momento.", CodigoError.DEMASIADAS_SOLICITUDES.name());
	}

	/**
	 * Devuelve la regla aplicable o null si la petición no está limitada. La ruta se normaliza (decodificada, sin
	 * ;params, sin barra final, minúsculas). Todo POST bajo /api/v1/auth/ queda limitado: login con su regla y el
	 * resto (refresh, logout y rutas desconocidas) comparte la de refresh.
	 */
	private Regla reglaDe(HttpServletRequest request) {
		if (!"POST".equals(request.getMethod())) {
			return null;
		}
		String ruta = RutaSolicitud.normalizada(request);
		if (RUTA_REGISTRO.equals(ruta)) {
			return reglaRegistro;
		}
		if (RUTA_LOGIN.equals(ruta)) {
			return reglaLogin;
		}
		if (ruta.startsWith(PREFIJO_AUTH) || RUTA_AUTH.equals(ruta)) {
			return reglaRefresh;
		}
		return null;
	}

	/** Elimina las entradas cuya ventana ya terminó. */
	private void limpiar(long ahoraMs) {
		contadores.values().removeIf(v -> v.finMs() <= ahoraMs);
	}

	int tamano() {
		return contadores.size();
	}

}
