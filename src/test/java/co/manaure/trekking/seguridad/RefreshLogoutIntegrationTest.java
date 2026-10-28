package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import co.manaure.trekking.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/** HU-N06 contra PostgreSQL real: rotación, reutilización, vencido, inactivo, logout y concurrencia. */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class RefreshLogoutIntegrationTest {

	static final String CORREO = "admin-prueba@ejemplo.com";
	static final String CLAVE = "clave-admin-prueba-1";
	static final String XRW = "X-Requested-With";

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ObjectProvider<LimpiezaSesionesTask> limpieza;

	@BeforeEach
	@AfterEach
	void limpiar() {
		jdbc.update("delete from sesion_refresh");
		jdbc.update("update administrador set activo = true, intentos_fallidos = 0, bloqueado_hasta = null");
	}

	private record Sesion(String cookie, String body, MockHttpServletResponse respuesta) {
		long numero(String campo) {
			return ((Number) JsonPath.read(body, "$." + campo)).longValue();
		}
	}

	private static String valorCookie(MockHttpServletResponse r) {
		Cookie c = r.getCookie("refresh_token");
		return c == null ? null : c.getValue();
	}

	private Sesion login(boolean recordar) throws Exception {
		MvcResult r = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
				.content("{\"correo\":\"" + CORREO + "\",\"clave\":\"" + CLAVE + "\",\"recordar\":" + recordar + "}"))
				.andExpect(status().isOk()).andReturn();
		return new Sesion(valorCookie(r.getResponse()), r.getResponse().getContentAsString(), r.getResponse());
	}

	private MockHttpServletResponse refresh(String cookie) throws Exception {
		var req = post("/api/v1/auth/refresh").header(XRW, "XMLHttpRequest");
		if (cookie != null) {
			req = req.cookie(new Cookie("refresh_token", cookie));
		}
		return mvc.perform(req).andReturn().getResponse();
	}

	private String familia() {
		return jdbc.queryForObject("select familia::text from sesion_refresh limit 1", String.class);
	}

	private int contarAuditoria(String accion, String familia) {
		return jdbc.queryForObject("select count(*) from auditoria where accion = ? and entidad_ref = ?",
				Integer.class, accion, familia);
	}

	@Test
	void refreshConCookieValidaDaAccessNuevoYCookieNuevaDistinta() throws Exception {
		Sesion inicio = login(true);
		Thread.sleep(20);

		MockHttpServletResponse r = refresh(inicio.cookie());

		assertThat(r.getStatus()).isEqualTo(200);
		String nueva = valorCookie(r);
		assertThat(nueva).isNotBlank().isNotEqualTo(inicio.cookie());
		String set = r.getHeader("Set-Cookie");
		assertThat(set).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth")
				.contains("Max-Age=");
		String body = r.getContentAsString();
		assertThat((String) JsonPath.read(body, "$.token")).isNotBlank().isNotEqualTo(JsonPath.read(inicio.body(), "$.token"));
		assertThat((String) JsonPath.read(body, "$.tipo")).isEqualTo("Bearer");
		assertThat((Boolean) JsonPath.read(body, "$.recordada")).isTrue();
		long expira = ((Number) JsonPath.read(body, "$.expiraEn")).longValue();
		assertThat(expira - System.currentTimeMillis()).isBetween(14 * 60_000L, 15 * 60_000L + 5_000L);
	}

	@Test
	void elAnteriorQuedaUsadoYEnlazadoMismaFamiliaYSinExtenderLaSesion() throws Exception {
		Sesion inicio = login(true);
		Thread.sleep(20);
		MockHttpServletResponse r = refresh(inicio.cookie());
		String nueva = valorCookie(r);

		List<java.util.Map<String, Object>> filas = jdbc.queryForList(
				"select id, familia::text as familia, token_hash, usado_en, revocado_en, reemplazado_por, expira_en "
						+ "from sesion_refresh order by id");
		assertThat(filas).hasSize(2);
		var padre = filas.get(0);
		var hijo = filas.get(1);
		assertThat(padre.get("token_hash")).isEqualTo(RefreshTokenService.hash(inicio.cookie()));
		assertThat(hijo.get("token_hash")).isEqualTo(RefreshTokenService.hash(nueva));
		assertThat(padre.get("usado_en")).isNotNull();
		assertThat(padre.get("reemplazado_por")).isEqualTo(hijo.get("id"));
		assertThat(hijo.get("usado_en")).isNull();
		assertThat(padre.get("familia")).isEqualTo(hijo.get("familia"));
		assertThat(padre.get("expira_en")).isEqualTo(hijo.get("expira_en"));

		String body = r.getContentAsString();
		assertThat(((Number) JsonPath.read(body, "$.iniciadaEn")).longValue()).isEqualTo(inicio.numero("iniciadaEn"));
		assertThat(((Number) JsonPath.read(body, "$.sesionExpiraEn")).longValue())
				.isEqualTo(inicio.numero("sesionExpiraEn"));
	}

	@Test
	void unRefreshYaUsadoDa401RevocaLaFamiliaCompletaYAudita() throws Exception {
		Sesion inicio = login(true);
		String segundo = valorCookie(refresh(inicio.cookie()));
		String tercero = valorCookie(refresh(segundo));
		String familia = familia();

		MockHttpServletResponse reuso = refresh(inicio.cookie());

		assertThat(reuso.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(reuso.getContentAsString(), "$.code")).isEqualTo("SESION_INVALIDA");
		assertThat(reuso.getHeader("Set-Cookie")).contains("Max-Age=0");
		assertThat(jdbc.queryForObject("select count(*) from sesion_refresh where revocado_en is null", Integer.class))
				.isZero();
		assertThat(contarAuditoria("SESION_REUTILIZADA", familia)).isEqualTo(1);
		// El último refresh emitido ya no sirve.
		assertThat(refresh(tercero).getStatus()).isEqualTo(401);
		assertThat(contarAuditoria("SESION_REUTILIZADA", familia)).isEqualTo(1);
	}

	@Test
	void refreshVencidoDa401YBorraLaCookie() throws Exception {
		Sesion inicio = login(false);
		jdbc.update("update sesion_refresh set expira_en = now() - interval '1 minute'");

		MockHttpServletResponse r = refresh(inicio.cookie());

		assertThat(r.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(r.getContentAsString(), "$.code")).isEqualTo("SESION_INVALIDA");
		assertThat(r.getHeader("Set-Cookie")).contains("refresh_token=;").contains("Max-Age=0");
	}

	@Test
	void refreshInexistenteOAusenteDa401YBorraLaCookie() throws Exception {
		for (String cookie : new String[] { "token-que-no-existe", null }) {
			MockHttpServletResponse r = refresh(cookie);
			assertThat(r.getStatus()).isEqualTo(401);
			assertThat((String) JsonPath.read(r.getContentAsString(), "$.code")).isEqualTo("SESION_INVALIDA");
			assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=0");
		}
	}

	@Test
	void administradorInactivoDa401() throws Exception {
		Sesion inicio = login(true);
		jdbc.update("update administrador set activo = false");

		MockHttpServletResponse r = refresh(inicio.cookie());

		assertThat(r.getStatus()).isEqualTo(401);
		assertThat((String) JsonPath.read(r.getContentAsString(), "$.code")).isEqualTo("SESION_INVALIDA");
		assertThat(r.getHeader("Set-Cookie")).contains("Max-Age=0");
		assertThat(jdbc.queryForObject("select count(*) from sesion_refresh where revocado_en is null", Integer.class))
				.isZero();
	}

	@Test
	void sinCabeceraRequestedWithRefreshYLogoutDan403SinConsumirElToken() throws Exception {
		Sesion inicio = login(true);
		Cookie cookie = new Cookie("refresh_token", inicio.cookie());

		for (String ruta : new String[] { "/api/v1/auth/refresh", "/api/v1/auth/logout" }) {
			mvc.perform(post(ruta).cookie(cookie)).andExpect(status().isForbidden())
					.andExpect(jsonPath("$.code").value("CABECERA_REQUERIDA"))
					.andExpect(jsonPath("$.detail").value("Falta la cabecera X-Requested-With."));
		}
		assertThat(refresh(inicio.cookie()).getStatus()).isEqualTo(200);
	}

	@Test
	void logoutRevocaLaFamiliaBorraLaCookieYElRefreshYaNoSirve() throws Exception {
		Sesion inicio = login(true);
		String vigente = valorCookie(refresh(inicio.cookie()));
		String familia = familia();

		MockHttpServletResponse r = mvc.perform(post("/api/v1/auth/logout").header(XRW, "XMLHttpRequest")
				.cookie(new Cookie("refresh_token", vigente))).andExpect(status().isNoContent())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")))
				.andReturn().getResponse();

		assertThat(r.getContentAsString()).isEmpty();
		assertThat(jdbc.queryForObject("select count(*) from sesion_refresh where revocado_en is null", Integer.class))
				.isZero();
		assertThat(contarAuditoria("CERRAR_SESION", familia)).isEqualTo(1);
		assertThat(refresh(vigente).getStatus()).isEqualTo(401);
	}

	@Test
	void logoutSinCookieOConCookieDesconocidaEsIdempotente() throws Exception {
		mvc.perform(post("/api/v1/auth/logout").header(XRW, "XMLHttpRequest")).andExpect(status().isNoContent())
				.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
		mvc.perform(post("/api/v1/auth/logout").header(XRW, "XMLHttpRequest").cookie(new Cookie("refresh_token", "x")))
				.andExpect(status().isNoContent());
		Sesion inicio = login(false);
		for (int i = 0; i < 2; i++) {
			mvc.perform(post("/api/v1/auth/logout").header(XRW, "XMLHttpRequest")
					.cookie(new Cookie("refresh_token", inicio.cookie()))).andExpect(status().isNoContent());
		}
	}

	@Test
	void dosRefreshSimultaneosConElMismoTokenNuncaDanDosHijosValidos() throws Exception {
		Sesion inicio = login(true);
		int hilos = 6;
		ExecutorService pool = Executors.newFixedThreadPool(hilos);
		CountDownLatch salida = new CountDownLatch(1);
		List<Future<Integer>> resultados = new ArrayList<>();
		for (int i = 0; i < hilos; i++) {
			Callable<Integer> tarea = () -> {
				salida.await();
				return refresh(inicio.cookie()).getStatus();
			};
			resultados.add(pool.submit(tarea));
		}
		salida.countDown();
		int ok = 0;
		for (Future<Integer> f : resultados) {
			int s = f.get();
			assertThat(s).isIn(200, 401);
			if (s == 200) {
				ok++;
			}
		}
		pool.shutdown();

		assertThat(ok).isEqualTo(1);
		// Padre + un solo hijo; no existen dos hijos de la misma familia.
		assertThat(jdbc.queryForObject("select count(*) from sesion_refresh", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from sesion_refresh where reemplazado_por is not null",
				Integer.class)).isEqualTo(1);
	}

	@Test
	void laLimpiezaProgramadaEstaApagadaPorDefectoEnLasPruebas() {
		assertThat(limpieza.getIfAvailable()).isNull();
	}

}
