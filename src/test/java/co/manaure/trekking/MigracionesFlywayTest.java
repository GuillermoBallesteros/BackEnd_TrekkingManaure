package co.manaure.trekking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * HU-N01: las migraciones se aplican en orden V1 a V19 sobre una base nueva; V9 retira el modelo anterior
 * y las secuencias de códigos arrancan en 1. Usa una base aparte dentro del mismo contenedor.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigracionesFlywayTest {

	private static final String BASE = "migraciones_prueba";
	private static final List<String> TABLAS_VIEJAS = List.of("auditoria", "abono", "ficha_salud", "persona",
			"inscripcion", "administrador", "configuracion_evento");

	@Autowired
	PostgreSQLContainer contenedor;

	@Autowired
	JdbcTemplate jdbcApp;

	private Flyway flyway(String objetivo) {
		String url = contenedor.getJdbcUrl().replace("/" + contenedor.getDatabaseName(), "/" + BASE);
		var config = Flyway.configure()
				.dataSource(url, contenedor.getUsername(), contenedor.getPassword())
				.locations("classpath:db/migration");
		if (objetivo != null) {
			config.target(objetivo);
		}
		return config.load();
	}

	private JdbcTemplate jdbcBase() {
		String url = contenedor.getJdbcUrl().replace("/" + contenedor.getDatabaseName(), "/" + BASE);
		return new JdbcTemplate(new DriverManagerDataSource(url, contenedor.getUsername(), contenedor.getPassword()));
	}

	private boolean existeTabla(JdbcTemplate jdbc, String tabla) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from information_schema.tables where table_schema = 'public' and table_name = ?)",
				Boolean.class, tabla));
	}

	private boolean existeSecuencia(JdbcTemplate jdbc, String secuencia) {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"select exists (select 1 from information_schema.sequences where sequence_schema = 'public' and sequence_name = ?)",
				Boolean.class, secuencia));
	}

	@Test
	void flywayAplicaV9aV18SobreUnaBaseConV1aV8() {
		jdbcApp.execute("CREATE DATABASE " + BASE);
		JdbcTemplate jdbc = jdbcBase();

		flyway("8").migrate();
		for (String tabla : TABLAS_VIEJAS) {
			assertThat(existeTabla(jdbc, tabla)).as("antes de V9 existe " + tabla).isTrue();
		}
		assertThat(existeSecuencia(jdbc, "inscripcion_codigo_seq")).isTrue();

		// V9 retira el modelo anterior (se comprueba aislada, antes de que V11 en adelante recreen algunas tablas).
		flyway("9").migrate();
		for (String tabla : TABLAS_VIEJAS) {
			assertThat(existeTabla(jdbc, tabla)).as("V9 elimina " + tabla).isFalse();
		}
		assertThat(existeSecuencia(jdbc, "inscripcion_codigo_seq")).isFalse();
		assertThat(jdbc.queryForObject(
				"select exists (select 1 from pg_extension where extname = 'unaccent')", Boolean.class)).isTrue();

		// V10 a V17, y datos en el formato anterior a las validaciones del formulario (tildes y minúsculas).
		flyway("17").migrate();
		jdbc.update("""
				insert into persona (nombres, apellidos, tipo_documento, documento, fecha_nacimiento, genero,
				    telefono_pais, telefono_numero, correo, ciudad, eps, eps_otra)
				values ('maría josé', 'muñoz  peña', 'CC', '54876234', date '1990-05-14', 'Femenino', 'CO',
				    '3001234567', 'ana@example.com', 'Medellín', 'Otra', 'Medimás')""");
		long persona = jdbc.queryForObject("select id from persona", Long.class);
		jdbc.update("""
				insert into contacto_emergencia (persona_id, nombre, telefono_pais, telefono_numero, parentesco)
				values (?, 'luis pérez', 'CO', '3157654321', 'Madre')""", persona);

		// V18 convierte lo existente a la forma normalizada y añade las restricciones nuevas.
		flyway(null).migrate();
		assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where success", Integer.class))
				.isEqualTo(19);
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success and version is not null",
				Integer.class)).isEqualTo(19);
		Map<String, Object> convertida = jdbc.queryForMap(
				"select nombres, apellidos, ciudad, eps_otra, documento, correo from persona where id = ?", persona);
		assertThat(convertida).containsEntry("nombres", "MARIA JOSE").containsEntry("apellidos", "MUÑOZ PEÑA")
				.containsEntry("ciudad", "Medellin").containsEntry("eps_otra", "Medimas")
				.containsEntry("documento", "54876234").containsEntry("correo", "ana@example.com");
		assertThat(jdbc.queryForObject("select nombre from contacto_emergencia where persona_id = ?", String.class,
				persona)).isEqualTo("LUIS PEREZ");

		// Secuencias: 1, 2, 3...
		assertThat(jdbc.queryForList("select nextval('inscripcion_codigo_seq') from generate_series(1,3)", Long.class))
				.containsExactly(1L, 2L, 3L);
		assertThat(jdbc.queryForList("select nextval('pago_codigo_seq') from generate_series(1,3)", Long.class))
				.containsExactly(1L, 2L, 3L);
	}

	@Test
	void laAplicacionArrancaConValidateSobreElEsquemaCompleto() {
		// El contexto ya cargó con ddl-auto=validate: si una entidad no coincidiera con la base, no habría arrancado.
		assertThat(jdbcApp.queryForObject("select max(version::int) from flyway_schema_history where success",
				Integer.class)).isEqualTo(19);
	}

}
