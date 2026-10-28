package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import co.manaure.trekking.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;

/**
 * Con la ventana de gracia configurada, el segundo refresh de la misma pestaña (carrera legítima) recibe 409
 * RECURSO_OCUPADO y no revoca la familia: el hijo emitido por el primero sigue sirviendo.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(properties = "app.seguridad.refresh-gracia-segundos=30")
class RefreshGraciaIntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		jdbc.update("delete from sesion_refresh");
		jdbc.update("update administrador set activo = true, intentos_fallidos = 0, bloqueado_hasta = null");
	}

	private MockHttpServletResponse refresh(String cookie) throws Exception {
		return mvc.perform(post("/api/v1/auth/refresh").header("X-Requested-With", "XMLHttpRequest")
				.cookie(new Cookie("refresh_token", cookie))).andReturn().getResponse();
	}

	@Test
	void unSegundoRefreshDentroDeLaGraciaDa409SinRevocarLaFamilia() throws Exception {
		MockHttpServletResponse login = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
				.content("{\"correo\":\"admin-prueba@ejemplo.com\",\"clave\":\"clave-admin-prueba-1\",\"recordar\":true}"))
				.andReturn().getResponse();
		String original = login.getCookie("refresh_token").getValue();
		String hijo = refresh(original).getCookie("refresh_token").getValue();

		MockHttpServletResponse carrera = refresh(original);

		assertThat(carrera.getStatus()).isEqualTo(409);
		assertThat((String) JsonPath.read(carrera.getContentAsString(), "$.code")).isEqualTo("RECURSO_OCUPADO");
		assertThat(carrera.getHeader("Set-Cookie")).isNull();
		assertThat(jdbc.queryForObject("select count(*) from sesion_refresh where revocado_en is not null", Integer.class))
				.isZero();
		assertThat(refresh(hijo).getStatus()).isEqualTo(200);
		// Con el hijo ya usado, volver a presentar el original sí es una reutilización.
		assertThat(refresh(original).getStatus()).isEqualTo(401);
	}

}
