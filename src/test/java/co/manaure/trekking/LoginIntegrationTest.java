package co.manaure.trekking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jayway.jsonpath.JsonPath;

import co.manaure.trekking.administrador.Administrador;
import co.manaure.trekking.administrador.AdministradorInicialRunner;
import co.manaure.trekking.administrador.AdministradorRepository;
import co.manaure.trekking.seguridad.AuthService;
import co.manaure.trekking.seguridad.IntentosExcedidosException;
import co.manaure.trekking.seguridad.dto.LoginRequest;

/** HU-N05 con PostgreSQL 18 real: administrador inicial, login, cookie, bloqueo persistido y control de acceso. */
@Import({ TestcontainersConfiguration.class, LoginIntegrationTest.AdminPruebaController.class })
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoginIntegrationTest {

	/** Controlador solo de pruebas: aún no existe ningún endpoint real bajo /api/v1/admin. */
	@TestComponent
	@RestController
	@RequestMapping("/api/v1/admin/prueba-login")
	static class AdminPruebaController {
		@GetMapping
		String ping() {
			return "ok";
		}
	}

	private static final String CLAVE = "clave-de-prueba-1";
	private static final AtomicInteger SECUENCIA = new AtomicInteger();

	@Autowired
	MockMvc mvc;
	@Autowired
	AdministradorRepository repositorio;
	@Autowired
	PasswordEncoder encoder;
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	JwtDecoder jwtDecoder;
	@Autowired
	ApplicationContext contexto;

	/** Crea un administrador exclusivo de la prueba (la prueba no comparte contadores con otras). */
	private String crearAdmin() {
		String correo = "prueba" + SECUENCIA.incrementAndGet() + "-" + System.nanoTime() + "@ejemplo.com";
		repositorio.save(new Administrador(correo, encoder.encode(CLAVE)));
		return correo;
	}

	private long idDe(String correo) {
		return jdbc.queryForObject("select id from administrador where correo = ?", Long.class, correo);
	}

	private ResultActions login(String correo, String clave, Boolean recordar) throws Exception {
		return loginDesde("127.0.0.1", correo, clave, recordar);
	}

	private ResultActions loginDesde(String ip, String correo, String clave, Boolean recordar) throws Exception {
		String cuerpo = "{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\""
				+ (recordar == null ? "" : ",\"recordar\":" + recordar) + "}";
		return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(cuerpo)
				.with(r -> {
					r.setRemoteAddr(ip);
					return r;
				}));
	}

	private static String cookie(MvcResult r) {
		return r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
	}

	private static String valorCookie(String setCookie) {
		return setCookie.substring("refresh_token=".length(), setCookie.indexOf(';'));
	}

	private static String sha256(String texto) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
	}

	// ---- Administrador inicial ----

	@Test
	void elAdministradorInicialSeCreoConBCryptCosto12YPermiteEntrar() throws Exception {
		Administrador a = repositorio.findByCorreo("admin-prueba@ejemplo.com").orElseThrow();
		assertThat(a.getClaveHash()).startsWith("$2a$12$").doesNotContain("clave-admin-prueba-1");
		assertThat(encoder.matches("clave-admin-prueba-1", a.getClaveHash())).isTrue();

		login("admin-prueba@ejemplo.com", "clave-admin-prueba-1", false).andExpect(status().isOk());
	}

	@Test
	void conUnAdministradorExistenteElRunnerNoCreaOtroNiCambiaLaClave() {
		long antes = repositorio.count();
		String hashAntes = repositorio.findByCorreo("admin-prueba@ejemplo.com").orElseThrow().getClaveHash();

		new AdministradorInicialRunner(repositorio, encoder, "nuevo@ejemplo.com", "otra-clave-larga-1").run(null);
		new AdministradorInicialRunner(repositorio, encoder, "admin-prueba@ejemplo.com", "otra-clave-larga-2").run(null);

		assertThat(repositorio.count()).isEqualTo(antes);
		assertThat(repositorio.findByCorreo("nuevo@ejemplo.com")).isEmpty();
		assertThat(repositorio.findByCorreo("admin-prueba@ejemplo.com").orElseThrow().getClaveHash())
				.isEqualTo(hashAntes);
	}

	// ---- Sesión ----

	@Test
	void credencialesValidasDanLaSesionYUnJwtConLosClaimsDelDiseno() throws Exception {
		String correo = crearAdmin();
		long antes = Instant.now().toEpochMilli();

		MvcResult r = login("  " + correo.toUpperCase() + "  ", CLAVE, true).andExpect(status().isOk())
				.andExpect(jsonPath("$.tipo").value("Bearer"))
				.andExpect(jsonPath("$.correo").value(correo))
				.andExpect(jsonPath("$.recordada").value(true))
				.andReturn();
		String cuerpo = r.getResponse().getContentAsString();
		String token = JsonPath.read(cuerpo, "$.token");
		long iniciada = ((Number) JsonPath.read(cuerpo, "$.iniciadaEn")).longValue();
		long expira = ((Number) JsonPath.read(cuerpo, "$.expiraEn")).longValue();
		long sesionExpira = ((Number) JsonPath.read(cuerpo, "$.sesionExpiraEn")).longValue();

		assertThat(JsonPath.<String>read(cuerpo, "$.nombre")).isEqualTo(
				co.manaure.trekking.seguridad.ValidadorCredenciales.nombreDesdeCorreo(correo));
		assertThat(iniciada).isBetween(antes, Instant.now().toEpochMilli());
		assertThat(expira - iniciada).isEqualTo(Duration.ofMinutes(15).toMillis());
		assertThat(sesionExpira - iniciada).isEqualTo(Duration.ofDays(7).toMillis());

		Jwt jwt = jwtDecoder.decode(token);
		assertThat(jwt.getSubject()).isEqualTo(Long.toString(idDe(correo)));
		assertThat(jwt.<String>getClaim("email")).isEqualTo(correo);
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
		assertThat(jwt.getId()).isNotBlank();
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		// El claim exp del JWT va en segundos: coincide con expiraEn truncado al segundo.
		assertThat(jwt.getExpiresAt().toEpochMilli()).isEqualTo(expira / 1000 * 1000);
	}

	@Test
	void conRecordarLaCookieLlevaLosAtributosExactos() throws Exception {
		String c = cookie(login(crearAdmin(), CLAVE, true).andExpect(status().isOk()).andReturn());

		assertThat(c).startsWith("refresh_token=").contains("; Path=/api/v1/auth").contains("; HttpOnly")
				.contains("; Secure").contains("; SameSite=Strict").contains("; Max-Age=604800");
	}

	@Test
	void sinRecordarLaCookieNoLlevaMaxAgeYLaSesionDura8Horas() throws Exception {
		MvcResult r = login(crearAdmin(), CLAVE, false).andExpect(status().isOk())
				.andExpect(jsonPath("$.recordada").value(false)).andReturn();
		String c = cookie(r);
		String cuerpo = r.getResponse().getContentAsString();
		long iniciada = ((Number) JsonPath.read(cuerpo, "$.iniciadaEn")).longValue();
		long sesionExpira = ((Number) JsonPath.read(cuerpo, "$.sesionExpiraEn")).longValue();

		assertThat(c).contains("; Path=/api/v1/auth").contains("; HttpOnly").contains("; Secure")
				.contains("; SameSite=Strict").doesNotContain("Max-Age").doesNotContain("Expires");
		assertThat(sesionExpira - iniciada).isEqualTo(Duration.ofHours(8).toMillis());
	}

	@Test
	void enLaBaseSoloExisteElSha256DelRefreshYNoElValorDeLaCookie() throws Exception {
		String correo = crearAdmin();
		String token = valorCookie(cookie(login(correo, CLAVE, true).andReturn()));

		List<Map<String, Object>> filas = jdbc.queryForList(
				"select * from sesion_refresh where administrador_id = ?", idDe(correo));
		assertThat(filas).hasSize(1);
		Map<String, Object> fila = filas.get(0);
		assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+"); // 256 bits en base64url
		assertThat(((String) fila.get("token_hash")).trim()).isEqualTo(sha256(token)).hasSize(64);
		assertThat(fila.values().stream().map(String::valueOf)).doesNotContain(token);
		assertThat(fila.get("familia")).isNotNull();
		assertThat(fila.get("recordada")).isEqualTo(true);
		assertThat(fila.get("usado_en")).isNull();
		assertThat(fila.get("revocado_en")).isNull();
		assertThat(fila.get("reemplazado_por")).isNull();
		assertThat(fila.get("expira_en")).isNotNull();
	}

	@Test
	void dosInicioDeSesionGeneranFamiliasYTokensDistintos() throws Exception {
		String correo = crearAdmin();
		String t1 = valorCookie(cookie(login(correo, CLAVE, false).andReturn()));
		String t2 = valorCookie(cookie(login(correo, CLAVE, false).andReturn()));

		assertThat(t1).isNotEqualTo(t2);
		assertThat(jdbc.queryForObject("select count(distinct familia) from sesion_refresh where administrador_id = ?",
				Integer.class, idDe(correo))).isEqualTo(2);
	}

	// ---- Credenciales inválidas ----

	@Test
	void inexistenteClaveIncorrectaYCuentaInactivaDanElMismo401() throws Exception {
		String correo = crearAdmin();
		String inactivo = crearAdmin();
		jdbc.update("update administrador set activo = false where correo = ?", inactivo);

		MvcResult mala = login(correo, "clave-incorrecta-9", false).andExpect(status().isUnauthorized()).andReturn();
		MvcResult inexistente = login("fantasma@ejemplo.com", "clave-incorrecta-9", false)
				.andExpect(status().isUnauthorized()).andReturn();
		MvcResult desactivado = login(inactivo, CLAVE, false).andExpect(status().isUnauthorized()).andReturn();

		String cuerpo = mala.getResponse().getContentAsString();
		assertThat(cuerpo).contains("CREDENCIALES_INVALIDAS").contains("Correo o contraseña incorrectos.");
		assertThat(inexistente.getResponse().getContentAsString()).isEqualTo(cuerpo);
		assertThat(desactivado.getResponse().getContentAsString()).isEqualTo(cuerpo);
		assertThat(cookie(mala)).isNull();
		assertThat(cookie(desactivado)).isNull();
	}

	@Test
	void datosConFormatoInvalidoDan400ConCamposDelFront() throws Exception {
		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"no-es-correo\",\"clave\":\"corta\",\"recordar\":false}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DATOS_INVALIDOS"))
				.andExpect(jsonPath("$.detail").value("Revisa los datos marcados."))
				.andExpect(jsonPath("$.campos.correo").value("Escribe un correo válido, por ejemplo nombre@dominio.com."))
				.andExpect(jsonPath("$.campos.clave").value("La contraseña debe tener al menos 8 caracteres."));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"a@ejemplo.com\",\"clave\":\" con-espacio-inicial\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.clave").value("La contraseña no puede empezar ni terminar con espacios."))
				.andExpect(jsonPath("$.campos.correo").doesNotExist());

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"correo\":\"a@ejemplo.com\",\"clave\":\"" + "x".repeat(129) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.clave").value("La contraseña no puede superar 128 caracteres."));

		mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.campos.correo").value("Escribe tu correo."))
				.andExpect(jsonPath("$.campos.clave").value("Escribe tu contraseña."));
	}

	// ---- Bloqueo persistido por pareja (IP, correo) ----

	private static String hashCorreo(String correo) throws Exception {
		return sha256(correo);
	}

	private void fallar(String ip, String correo, int veces) throws Exception {
		for (int i = 0; i < veces; i++) {
			loginDesde(ip, correo, "clave-incorrecta-9", false).andExpect(status().isUnauthorized());
		}
	}

	@Test
	void cincoFallosBloqueanSoloEsaParejaConRetryAfterYSeAuditaSinDatos() throws Exception {
		String correo = crearAdmin();
		fallar("127.0.0.1", correo, 5);

		MvcResult r = login(correo, CLAVE, false).andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("INTENTOS_EXCEDIDOS")).andReturn();
		long segundos = Long.parseLong(r.getResponse().getHeader("Retry-After"));
		assertThat(segundos).isBetween(1L, 900L);
		assertThat(cookie(r)).isNull();

		Map<String, Object> fila = jdbc.queryForMap(
				"select ip, correo_hash, fallos, bloqueado_hasta from intento_login where correo_hash = ?",
				hashCorreo(correo));
		assertThat(fila.get("fallos")).isEqualTo(5);
		assertThat(fila.get("bloqueado_hasta")).isNotNull();
		assertThat(fila.values().stream().map(String::valueOf)).noneMatch(v -> v.contains(correo));

		List<Map<String, Object>> auditorias = jdbc.queryForList(
				"select entidad_ref, coalesce(detalle, '') as detalle from auditoria "
						+ "where accion = 'BLOQUEO_CUENTA' and administrador_id = ?", idDe(correo));
		assertThat(auditorias).hasSize(1);
		assertThat(auditorias.get(0).values().stream().map(String::valueOf))
				.noneMatch(v -> v.contains(correo) || v.contains(CLAVE) || v.contains("@"));
	}

	@Test
	void otraIpConLaClaveCorrectaEntraAunqueLaParejaEsteBloqueada() throws Exception {
		String correo = crearAdmin();
		fallar("203.0.113.10", correo, 5);

		loginDesde("203.0.113.10", correo, CLAVE, false).andExpect(status().isTooManyRequests());
		loginDesde("203.0.113.11", correo, CLAVE, false).andExpect(status().isOk());
	}

	@Test
	void correoInexistenteSeCuentaYBloqueaIgualQueUnoExistente() throws Exception {
		String existente = crearAdmin();
		String fantasma = "fantasma" + System.nanoTime() + "@ejemplo.com";
		fallar("203.0.113.20", existente, 5);
		fallar("203.0.113.20", fantasma, 5);

		MvcResult a = loginDesde("203.0.113.20", existente, "otra-clave-cualquiera", false)
				.andExpect(status().isTooManyRequests()).andReturn();
		MvcResult b = loginDesde("203.0.113.20", fantasma, "otra-clave-cualquiera", false)
				.andExpect(status().isTooManyRequests()).andReturn();
		assertThat(b.getResponse().getContentAsString()).isEqualTo(a.getResponse().getContentAsString());
		assertThat(b.getResponse().getHeader("Retry-After")).isNotNull();
		assertThat(jdbc.queryForObject("select count(*) from intento_login where correo_hash = ? and fallos = 5",
				Integer.class, hashCorreo(fantasma))).isEqualTo(1);
	}

	@Test
	void elBloqueoSobreviveAUnaNuevaInstanciaDelServicioSobreLaMismaBase() throws Exception {
		String correo = crearAdmin();
		fallar("127.0.0.1", correo, 5);

		// Simula un reinicio: servicio nuevo (sin ningun estado en memoria) sobre la misma base.
		AuthService nuevo = contexto.getAutowireCapableBeanFactory().createBean(AuthService.class);

		assertThatThrownBy(() -> nuevo.login(new LoginRequest(correo, CLAVE, false), "127.0.0.1"))
				.isInstanceOf(IntentosExcedidosException.class);
	}

	@Test
	void pasadoElPlazoSePuedeEntrarYElContadorSeReinicia() throws Exception {
		String correo = crearAdmin();
		fallar("127.0.0.1", correo, 5);
		login(correo, CLAVE, false).andExpect(status().isTooManyRequests());

		jdbc.update("update intento_login set bloqueado_hasta = now() - interval '1 second' where correo_hash = ?",
				hashCorreo(correo));
		login(correo, CLAVE, false).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select count(*) from intento_login where correo_hash = ?", Integer.class,
				hashCorreo(correo))).isZero();
		assertThat(jdbc.queryForObject("select count(*) from administrador where correo = ? and ultimo_acceso_en is not null",
				Integer.class, correo)).isEqualTo(1);
	}

	@Test
	void unFalloTrasUnBloqueoVencidoNoBloqueaDeNuevoDeInmediato() throws Exception {
		String correo = crearAdmin();
		fallar("127.0.0.1", correo, 5);
		jdbc.update("update intento_login set bloqueado_hasta = now() - interval '1 second' where correo_hash = ?",
				hashCorreo(correo));

		fallar("127.0.0.1", correo, 1);
		login(correo, CLAVE, false).andExpect(status().isOk());
	}

	@Test
	void unAccesoExitosoReiniciaLosFallosDeLaPareja() throws Exception {
		String correo = crearAdmin();
		fallar("127.0.0.1", correo, 3);
		assertThat(jdbc.queryForObject("select fallos from intento_login where correo_hash = ?", Integer.class,
				hashCorreo(correo))).isEqualTo(3);

		login(correo, CLAVE, false).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select count(*) from intento_login where correo_hash = ?", Integer.class,
				hashCorreo(correo))).isZero();
	}

	@Test
	void alAlcanzarElTopeGlobalSeAuditaPeroLaClaveCorrectaSigueEntrando() throws Exception {
		String correo = crearAdmin();
		// 49 fallos previos repartidos en otras IP (1 por IP): el siguiente fallo llega a 50.
		for (int i = 0; i < 49; i++) {
			jdbc.update("insert into intento_login (ip, correo_hash, fallos, ultimo_fallo_en) values (?, ?, 1, now())",
					"198.51.100." + i, hashCorreo(correo));
		}
		fallar("203.0.113.30", correo, 1);

		assertThat(jdbc.queryForObject("select count(*) from auditoria where accion = 'BLOQUEO_CUENTA' "
				+ "and administrador_id = ? and entidad_ref = 'LOGIN-GLOBAL'", Integer.class, idDe(correo))).isEqualTo(1);
		loginDesde("203.0.113.31", correo, CLAVE, false).andExpect(status().isOk());
	}

	@Test
	void laLimpiezaBorraLosIntentosViejosYConservaLosRecientes() throws Exception {
		String correo = crearAdmin();
		jdbc.update("insert into intento_login (ip, correo_hash, fallos, ultimo_fallo_en) values "
				+ "('1.1.1.1', ?, 1, now() - interval '3 days'), ('2.2.2.2', ?, 1, now())", hashCorreo(correo),
				hashCorreo(correo));

		int borrados = contexto.getBean(co.manaure.trekking.seguridad.IntentoLoginRepository.class)
				.borrarAntesDe(Instant.now().minus(Duration.ofDays(1)));

		assertThat(borrados).isGreaterThanOrEqualTo(1);
		assertThat(jdbc.queryForList("select ip from intento_login where correo_hash = ?", String.class,
				hashCorreo(correo))).containsExactly("2.2.2.2");
	}

	// ---- Auditoría y logs ----

	@Test
	void seAuditaIniciarSesionSinClaveNiCorreo() throws Exception {
		String correo = crearAdmin();
		login(correo, CLAVE, true).andExpect(status().isOk());

		List<Map<String, Object>> filas = jdbc.queryForList(
				"select entidad, entidad_ref, coalesce(detalle, '') as detalle from auditoria "
						+ "where accion = 'INICIAR_SESION' and administrador_id = ?", idDe(correo));
		assertThat(filas).hasSize(1);
		assertThat(filas.get(0).get("entidad")).isEqualTo("SESION");
		assertThat(filas.get(0).values().stream().map(String::valueOf)).noneMatch(
				v -> v.contains(correo) || v.contains(CLAVE) || v.contains("@"));
	}

	@Test
	void laClaveNuncaApareceEnLosLogs(CapturedOutput salida) throws Exception {
		String correo = crearAdmin();
		String claveRara = "Clave-Rara-Para-Logs-77";
		login(correo, claveRara, false).andExpect(status().isUnauthorized());
		MvcResult ok = login(correo, CLAVE, true).andExpect(status().isOk()).andReturn();
		String hash = repositorio.findByCorreo(correo).orElseThrow().getClaveHash();

		String log = salida.getOut() + salida.getErr();
		assertThat(log).doesNotContain(claveRara).doesNotContain(CLAVE).doesNotContain(hash)
				.doesNotContain(valorCookie(cookie(ok)))
				.doesNotContain((String) JsonPath.read(ok.getResponse().getContentAsString(), "$.token"));
	}

	// ---- Control de acceso ----

	@Test
	void elTokenDelLoginAbreLasRutasAdminYSinTokenDa401() throws Exception {
		mvc.perform(get("/api/v1/admin/prueba-login")).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));

		MvcResult r = login(crearAdmin(), CLAVE, false).andReturn();
		String token = JsonPath.read(r.getResponse().getContentAsString(), "$.token");

		mvc.perform(get("/api/v1/admin/prueba-login").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk());
	}

}
