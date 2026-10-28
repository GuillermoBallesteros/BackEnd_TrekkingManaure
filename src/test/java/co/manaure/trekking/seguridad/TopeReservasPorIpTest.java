package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;

/** H4: tope de 20 solicitudes por IP en 24 h a POST /reservas, con alerta única por IP y ventana. */
class TopeReservasPorIpTest {

	static final String RESERVAS = "/api/v1/reservas";

	RateLimitFilterTest.RelojMutable reloj = new RateLimitFilterTest.RelojMutable();
	AuditoriaService auditoria = mock(AuditoriaService.class);
	TopeReservasPorIp tope = new TopeReservasPorIp(reloj, auditoria, 20);
	RateLimitFilter filtro = new RateLimitFilter(reloj, 1_000_000, 20, 60, tope);
	ListAppender<ILoggingEvent> logs = new ListAppender<>();
	Logger logTope = (Logger) LoggerFactory.getLogger(TopeReservasPorIp.class);

	@BeforeEach
	void capturarLogs() {
		logs.start();
		logTope.addAppender(logs);
		logTope.setLevel(Level.DEBUG);
	}

	@AfterEach
	void soltarLogs() {
		logTope.detachAppender(logs);
	}

	private MockHttpServletResponse peticion(RateLimitFilter f, String metodo, String ruta, String ip) throws Exception {
		MockHttpServletRequest req = new MockHttpServletRequest(metodo, ruta);
		req.setRemoteAddr(ip);
		MockHttpServletResponse res = new MockHttpServletResponse();
		f.doFilter(req, res, new MockFilterChain());
		return res;
	}

	private MockHttpServletResponse peticion(String metodo, String ruta, String ip) throws Exception {
		return peticion(filtro, metodo, ruta, ip);
	}

	private void enviar(int n, String ip) throws Exception {
		for (int i = 0; i < n; i++) {
			peticion("POST", RESERVAS, ip);
		}
	}

	private void verificarAlertas(int veces) {
		verify(auditoria, times(veces)).registrar(isNull(), eq(AccionAuditoria.ALERTA_RESERVAS_IP),
				eq(EntidadAuditoria.RESERVA), startsWith("IP-"), anyString());
	}

	@Test
	void lasVeintePrimerasPasanYLaVeintiunaDa429ConRetryAfterHastaElFinDeLaVentana() throws Exception {
		for (int i = 0; i < 20; i++) {
			assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
		}
		reloj.ahora = reloj.ahora.plusSeconds(3600);
		MockHttpServletResponse r = peticion("POST", RESERVAS, "1.1.1.1");
		assertThat(r.getStatus()).isEqualTo(429);
		assertThat(r.getHeader("Retry-After")).isEqualTo(Long.toString(24 * 3600 - 3600));
		assertThat(r.getContentAsString()).contains("\"code\":\"DEMASIADAS_SOLICITUDES\"").doesNotContain("1.1.1.1");
		assertThat(r.getContentType()).startsWith("application/problem+json");
	}

	@Test
	void otraIpNoSeAfecta() throws Exception {
		enviar(25, "1.1.1.1");
		assertThat(peticion("POST", RESERVAS, "2.2.2.2").getStatus()).isEqualTo(200);
	}

	@Test
	void laAlertaSeDisparaUnaSolaVezAunqueLleguen50SolicitudesSinDatosPersonales() throws Exception {
		enviar(50, "203.0.113.77");

		verificarAlertas(1);
		List<String> alertas = logs.list.stream().filter(e -> e.getLevel() == Level.WARN)
				.map(ILoggingEvent::getFormattedMessage).toList();
		assertThat(alertas).hasSize(1);
		assertThat(alertas.get(0)).startsWith("ALERTA: la IP ").endsWith("superó 20 solicitudes de reserva en 24 horas")
				.doesNotContain("203.0.113.77");
	}

	@Test
	void lasSolicitudesPorDebajoDelTopeNoAlertan() throws Exception {
		enviar(20, "1.1.1.1");
		verificarAlertas(0);
		assertThat(logs.list).isEmpty();
	}

	@Test
	void laVentanaSeReiniciaAlAvanzar24HorasYPuedeAlertarDeNuevo() throws Exception {
		enviar(21, "1.1.1.1");
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(429);
		reloj.ahora = reloj.ahora.plusSeconds(24 * 3600 - 1);
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(429);
		reloj.ahora = reloj.ahora.plusSeconds(1);
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);

		enviar(20, "1.1.1.1");
		verificarAlertas(2);
	}

	@Test
	void noAfectaOtrasRutasNiOtrosMetodos() throws Exception {
		enviar(30, "1.1.1.1");
		assertThat(peticion("GET", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
		assertThat(peticion("GET", "/api/v1/config", "1.1.1.1").getStatus()).isEqualTo(200);
		assertThat(peticion("POST", "/api/v1/auth/login", "1.1.1.1").getStatus()).isEqualTo(200);
		assertThat(peticion("POST", "/api/v1/admin/reservas/PER-0001/pagos", "1.1.1.1").getStatus()).isEqualTo(200);
	}

	@Test
	void lasVariantesDeLaRutaCuentanEnElMismoTope() throws Exception {
		enviar(20, "1.1.1.1");
		assertThat(peticion("POST", "/API/V1/Reservas/", "1.1.1.1").getStatus()).isEqualTo(429);
	}

	@Test
	void elLimitePorMinutoSigueFuncionando() throws Exception {
		RateLimitFilter conMinuto = new RateLimitFilter(reloj, 10, 20, 60, tope);
		for (int i = 0; i < 10; i++) {
			assertThat(peticion(conMinuto, "POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(200);
		}
		MockHttpServletResponse r = peticion(conMinuto, "POST", RESERVAS, "1.1.1.1");
		assertThat(r.getStatus()).isEqualTo(429);
		// Es el límite por minuto (50 s hasta el siguiente minuto), no el diario.
		assertThat(r.getHeader("Retry-After")).isEqualTo("50");
		verificarAlertas(0);
	}

	@Test
	void limpiaLasEntradasVencidasSinRecorrerTodoEnCadaSolicitud() throws Exception {
		enviar(1, "1.1.1.1");
		enviar(1, "2.2.2.2");
		assertThat(tope.tamano()).isEqualTo(2);
		// Pasadas las 24 h y el intervalo de limpieza, la primera solicitud barre lo vencido.
		reloj.ahora = Instant.parse("2026-01-02T10:05:00Z");
		enviar(1, "3.3.3.3");
		assertThat(tope.tamano()).isEqualTo(1);
	}

	@Test
	void siFallaLaAuditoriaIgualResponde429() throws Exception {
		doThrow(new IllegalStateException("bd caída")).when(auditoria).registrar(isNull(),
				eq(AccionAuditoria.ALERTA_RESERVAS_IP), eq(EntidadAuditoria.RESERVA), anyString(), anyString());
		enviar(20, "1.1.1.1");
		assertThat(peticion("POST", RESERVAS, "1.1.1.1").getStatus()).isEqualTo(429);
	}

}
