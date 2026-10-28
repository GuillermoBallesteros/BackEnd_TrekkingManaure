package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import co.manaure.trekking.TestcontainersConfiguration;

/** HU-N06: la tarea programada borra los refresh vencidos hace más de 30 días y solo existe si se activa. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "app.seguridad.limpieza.activa=true")
class LimpiezaSesionesTaskTest {

	@Autowired
	LimpiezaSesionesTask tarea;

	@Autowired
	JdbcTemplate jdbc;

	@BeforeEach
	@AfterEach
	void limpiar() {
		jdbc.update("delete from sesion_refresh");
	}

	private void insertar(String hash, String vencimiento) {
		jdbc.update("insert into sesion_refresh (administrador_id, familia, token_hash, recordada, expira_en) "
				+ "select id, gen_random_uuid(), ?, true, now() + (?::interval) from administrador limit 1", hash,
				vencimiento);
	}

	@Test
	void borraSoloLosVencidosHaceMasDeTreintaDias() {
		insertar("a".repeat(64), "-31 days");
		insertar("b".repeat(64), "-29 days");
		insertar("c".repeat(64), "-1 minute");
		insertar("d".repeat(64), "7 days");

		tarea.limpiar();

		assertThat(jdbc.queryForList("select token_hash from sesion_refresh order by token_hash", String.class))
				.containsExactly("b".repeat(64), "c".repeat(64), "d".repeat(64));
	}

	@Test
	void estaProgramadaConUnaExpresionCron() throws Exception {
		Method metodo = LimpiezaSesionesTask.class.getMethod("limpiar");
		Scheduled programada = metodo.getAnnotation(Scheduled.class);
		assertThat(programada).isNotNull();
		assertThat(programada.cron()).isNotBlank();
	}

}
