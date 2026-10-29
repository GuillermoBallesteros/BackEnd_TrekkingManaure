package co.manaure.trekking.persona;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import co.manaure.trekking.TestcontainersConfiguration;

/** HU-N01: la ficha de salud se guarda cifrada en PostgreSQL y se lee descifrada; nada de salud en los logs. */
@ExtendWith(OutputCaptureExtension.class)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FichaSaludPersistenciaTest {

	@Autowired
	PersonaRepository personas;

	@Autowired
	FichaSaludRepository fichas;

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		jdbc.update("delete from ficha_salud");
		jdbc.update("delete from persona");
	}

	private Persona persona() {
		return personas.save(new Persona("MARIA", "PEREZ", "CC", "12345678", LocalDate.of(1990, 5, 14), "Femenino",
				"CO", "3001234567", "maria@example.com", "Valledupar", "Nueva EPS", null));
	}

	@Test
	void alergiaGuardadaCifradaConFormatoVersionBase64YLeidaDescifrada(CapturedOutput salida) {
		Persona p = persona();

		fichas.save(new FichaSalud(p, null, "Penicilina", null, true));

		String guardado = jdbc.queryForObject("select alergias_cifrada from ficha_salud where persona_id = ?",
				String.class, p.getId());
		assertThat(guardado).matches("1:[A-Za-z0-9+/]+={0,2}").doesNotContain("Penicilina");
		assertThat(jdbc.queryForObject("select enfermedad_cifrada from ficha_salud where persona_id = ?", String.class,
				p.getId())).isNull();
		assertThat(jdbc.queryForObject("select tiene_alergias from ficha_salud where persona_id = ?", Boolean.class,
				p.getId())).isTrue();

		FichaSalud leida = fichas.findById(p.getId()).orElseThrow();
		assertThat(leida.getAlergias()).isEqualTo("Penicilina");
		assertThat(leida.getEnfermedad()).isNull();
		assertThat(leida.isTieneAlergias()).isTrue();
		assertThat(leida.isTieneEnfermedad()).isFalse();

		assertThat(salida.getAll()).doesNotContain("Penicilina");
	}

}
