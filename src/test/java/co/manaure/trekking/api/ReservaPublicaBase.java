package co.manaure.trekking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import co.manaure.trekking.TestcontainersConfiguration;
import co.manaure.trekking.seguridad.JwtService;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de las pruebas de HU-N04: PostgreSQL 18 real (Testcontainers), reloj fijo en Bogotá y base limpia entre
 * pruebas. Las secuencias de códigos vuelven a 1 para que la primera reserva sea PER-0001 / PAG-0001.
 */
@Import({ TestcontainersConfiguration.class, RelojPrueba.Config.class })
@SpringBootTest
@AutoConfigureMockMvc
abstract class ReservaPublicaBase {

	@Autowired
	protected MockMvc mvc;
	@Autowired
	protected JdbcTemplate jdbc;
	@Autowired
	protected RelojPrueba reloj;
	@Autowired
	protected JwtService jwt;

	@BeforeEach
	void prepararBase() {
		limpiar();
		reloj.restablecer();
		jdbc.execute("alter sequence inscripcion_codigo_seq restart with 1");
		jdbc.execute("alter sequence pago_codigo_seq restart with 1");
	}

	@AfterEach
	void limpiarBase() {
		limpiar();
	}

	private void limpiar() {
		jdbc.update("delete from auditoria where entidad in ('PAGO', 'RESERVA')");
		for (String tabla : new String[] { "pago", "inscripcion_extra", "hotel_previo", "inscripcion_participante",
				"consentimiento_persona", "contacto_emergencia", "ficha_salud", "inscripcion", "persona" }) {
			jdbc.update("delete from " + tabla);
		}
		jdbc.update("""
				update configuracion_evento set cupos_carrera = 60, cupos_paramo = 60, precio_titular = 350000, valor_apartado = 50000,
				  precio_acompanante_sin_kit = 150000, precio_acompanante_con_kit = 350000,
				  precio_cuatrimoto = 150000, precio_parapente = 200000, precio_hotel_noche = 45000""");
	}

	protected ResultActions enviar(ReservaJson reserva) throws Exception {
		return enviar(reserva.json());
	}

	protected ResultActions enviar(String json) throws Exception {
		return mvc.perform(post("/api/v1/reservas").contentType(MediaType.APPLICATION_JSON).content(json));
	}

	protected int contar(String tabla) {
		return jdbc.queryForObject("select count(*) from " + tabla, Integer.class);
	}

	/** Id de una administradora de pruebas (revisado_por tiene clave foránea a administrador). */
	protected long adminId() {
		jdbc.update("""
				insert into administrador (correo, clave_hash) values ('revisora-pruebas@example.com', 'x')
				on conflict (correo) do nothing""");
		return jdbc.queryForObject("select id from administrador where correo = 'revisora-pruebas@example.com'",
				Long.class);
	}

	protected String tokenAdmin() {
		return jwt.emitir(adminId(), "revisora-pruebas@example.com", Instant.now()).token();
	}

	/** {@code PUT /api/v1/admin/pagos/{id}/revision} con el token de la administradora de pruebas. */
	protected ResultActions revisar(String codigoPago, String review, String reason) throws Exception {
		Map<String, Object> cuerpo = new LinkedHashMap<>();
		if (review != null) {
			cuerpo.put("review", review);
		}
		if (reason != null) {
			cuerpo.put("reason", reason);
		}
		return mvc.perform(put("/api/v1/admin/pagos/" + codigoPago + "/revision")
				.header("Authorization", "Bearer " + tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
				.content(new JsonMapper().writeValueAsString(cuerpo)));
	}

	/** {@code POST /api/v1/admin/reservas/{id}/pagos} con el cuerpo JSON crudo y el token de pruebas. */
	protected ResultActions pagoManual(String codigoReserva, String cuerpoJson) throws Exception {
		return mvc.perform(post("/api/v1/admin/reservas/" + codigoReserva + "/pagos")
				.header("Authorization", "Bearer " + tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoJson));
	}

	/** {@code PUT /api/v1/admin/reservas/{id}/autorizacion-menor} con el cuerpo JSON crudo y el token de pruebas. */
	protected ResultActions autorizacionMenor(String codigoReserva, String cuerpoJson) throws Exception {
		return mvc.perform(put("/api/v1/admin/reservas/" + codigoReserva + "/autorizacion-menor")
				.header("Authorization", "Bearer " + tokenAdmin()).contentType(MediaType.APPLICATION_JSON)
				.content(cuerpoJson));
	}

	/** Aprueba por el endpoint real todos los pagos de una inscripción (así ocupa cupo). */
	protected void aprobarPagos(String codigoInscripcion) throws Exception {
		List<String> codigos = jdbc.queryForList("""
				select codigo from pago where inscripcion_id = (select id from inscripcion where codigo = ?)
				order by id""", String.class, codigoInscripcion);
		for (String codigo : codigos) {
			revisar(codigo, "Aprobado", null).andExpect(status().isOk());
		}
	}

}
