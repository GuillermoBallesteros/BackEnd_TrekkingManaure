package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LimiteCuerpoFilterTest {

	private final LimiteCuerpoFilter filtro = new LimiteCuerpoFilter(64 * 1024);

	/** Simula un cuerpo sin Content-Length (chunked). */
	private static MockHttpServletRequest sinLongitud(byte[] cuerpo) {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/reservas") {
			@Override
			public long getContentLengthLong() {
				return -1;
			}
		};
		req.setContent(cuerpo);
		return req;
	}

	@Test
	void contentLengthMayorAlTopeDa413SinLlamarALaCadena() throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/reservas");
		req.setContent(new byte[70 * 1024]);
		MockHttpServletResponse res = new MockHttpServletResponse();
		boolean[] llamada = { false };

		filtro.doFilter(req, res, (rq, rs) -> llamada[0] = true);

		assertThat(res.getStatus()).isEqualTo(413);
		assertThat(llamada[0]).isFalse();
		assertThat(res.getContentAsString()).contains("\"code\":\"CUERPO_DEMASIADO_GRANDE\"")
				.contains("La solicitud es demasiado grande.");
		assertThat(res.getContentType()).startsWith("application/problem+json");
	}

	@Test
	void sinContentLengthSeCortaAlLeerMasDelTope() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();

		filtro.doFilter(sinLongitud(new byte[70 * 1024]), res, (rq, rs) -> rq.getInputStream().readAllBytes());

		assertThat(res.getStatus()).isEqualTo(413);
		assertThat(res.getContentAsString()).contains("CUERPO_DEMASIADO_GRANDE");
	}

	@Test
	void unCuerpoNormalPasa() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();
		int[] leidos = { 0 };

		filtro.doFilter(sinLongitud(new byte[10 * 1024]), res,
				(rq, rs) -> leidos[0] = rq.getInputStream().readAllBytes().length);

		assertThat(res.getStatus()).isEqualTo(200);
		assertThat(leidos[0]).isEqualTo(10 * 1024);
	}

	@Test
	void unCuerpoExactamenteEnElTopePasa() throws Exception {
		MockHttpServletResponse res = new MockHttpServletResponse();
		filtro.doFilter(sinLongitud(new byte[64 * 1024]), res, (rq, rs) -> rq.getInputStream().readAllBytes());
		assertThat(res.getStatus()).isEqualTo(200);
	}

	@Test
	void laExcepcionDeLecturaSeIdentificaEnLaCadenaDeCausas() {
		assertThat(CuerpoDemasiadoGrandeException.enCadena(
				new RuntimeException(new IOException(new CuerpoDemasiadoGrandeException())))).isTrue();
		assertThat(CuerpoDemasiadoGrandeException.enCadena(new RuntimeException("otra"))).isFalse();
		assertThatThrownBy(() -> {
			throw new CuerpoDemasiadoGrandeException();
		}).isInstanceOf(IOException.class);
	}

}
