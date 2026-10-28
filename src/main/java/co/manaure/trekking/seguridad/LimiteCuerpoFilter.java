package co.manaure.trekking.seguridad;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.web.filter.OncePerRequestFilter;

import co.manaure.trekking.comun.error.CodigoError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Tope de tamaño del cuerpo (hallazgo H5, diseño §10.3): 413 CUERPO_DEMASIADO_GRANDE si el cuerpo supera
 * {@code maxBytes}. Con Content-Length se rechaza antes de leer; sin él (chunked) se cuenta mientras se lee y se
 * corta al pasar el tope. La respuesta no repite nada de lo recibido.
 */
public class LimiteCuerpoFilter extends OncePerRequestFilter {

	public static final int MAX_BYTES_POR_DEFECTO = 64 * 1024;
	static final String DETALLE = "La solicitud es demasiado grande.";

	private final long maxBytes;

	public LimiteCuerpoFilter(long maxBytes) {
		this.maxBytes = maxBytes;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (request.getContentLengthLong() > maxBytes) {
			rechazar(response);
			return;
		}
		try {
			chain.doFilter(new Limitada(request, maxBytes), response);
		}
		catch (ServletException | IOException | RuntimeException ex) {
			if (CuerpoDemasiadoGrandeException.enCadena(ex) && !response.isCommitted()) {
				rechazar(response);
				return;
			}
			throw ex;
		}
	}

	private static void rechazar(HttpServletResponse response) throws IOException {
		response.resetBuffer();
		ProblemAuthenticationEntryPoint.escribir(response, 413, "Content Too Large", DETALLE,
				CodigoError.CUERPO_DEMASIADO_GRANDE.name());
	}

	private static final class Limitada extends HttpServletRequestWrapper {

		private final long max;
		private ServletInputStream flujo;

		Limitada(HttpServletRequest request, long max) {
			super(request);
			this.max = max;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (flujo == null) {
				flujo = new Contador(super.getInputStream(), max);
			}
			return flujo;
		}

		@Override
		public BufferedReader getReader() throws IOException {
			String codificacion = getCharacterEncoding();
			Charset charset = codificacion == null ? StandardCharsets.UTF_8 : Charset.forName(codificacion);
			return new BufferedReader(new InputStreamReader(getInputStream(), charset));
		}

	}

	private static final class Contador extends ServletInputStream {

		private final ServletInputStream origen;
		private final long max;
		private long leidos;

		Contador(ServletInputStream origen, long max) {
			this.origen = origen;
			this.max = max;
		}

		private void contar(long n) throws IOException {
			if (n > 0) {
				leidos += n;
				if (leidos > max) {
					throw new CuerpoDemasiadoGrandeException();
				}
			}
		}

		@Override
		public int read() throws IOException {
			int b = origen.read();
			if (b >= 0) {
				contar(1);
			}
			return b;
		}

		@Override
		public int read(byte[] buffer, int desde, int largo) throws IOException {
			int n = origen.read(buffer, desde, largo);
			contar(n);
			return n;
		}

		@Override
		public boolean isFinished() {
			return origen.isFinished();
		}

		@Override
		public boolean isReady() {
			return origen.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			origen.setReadListener(listener);
		}

	}

}
