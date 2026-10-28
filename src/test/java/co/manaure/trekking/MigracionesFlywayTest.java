package co.manaure.trekking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * HU-N01: las migraciones se aplican en orden V1 a V15 sobre una base nueva; V9 retira el modelo anterior
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
	void flywayAplicaV9aV17SobreUnaBaseConV1aV8() {
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

		// V10 a V17.
		flyway(null).migrate();
		assertThat(jdbc.queryForObject("select max(version::int) from flyway_schema_history where success", Integer.class))
				.isEqualTo(17);
		assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success and version is not null",
				Integer.class)).isEqualTo(17);

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
				Integer.class)).isEqualTo(17);
	}

}
