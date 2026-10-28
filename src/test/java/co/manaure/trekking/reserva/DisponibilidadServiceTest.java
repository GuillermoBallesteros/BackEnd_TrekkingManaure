package co.manaure.trekking.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;

class DisponibilidadServiceTest {

	@Test
	void todayUsaElRelojDeBogotaALas2330() {
		// 23:30 del 28 de octubre en Bogota = 04:30 UTC del 29: today debe seguir siendo el 28.
		Instant instante = Instant.parse("2026-10-29T04:30:00Z");
		Clock clock = Clock.fixed(instante, ZoneId.of("America/Bogota"));
		assertThat(LocalDate.ofInstant(instante, ZoneOffset.UTC)).isEqualTo(LocalDate.of(2026, 10, 29));

		ConfiguracionService cfgService = mock(ConfiguracionService.class);
		ConfiguracionEvento cfg = mock(ConfiguracionEvento.class);
		when(cfg.getCuposParamo()).thenReturn(60);
		when(cfg.getFechaCierre()).thenReturn(LocalDate.of(2026, 11, 9));
		when(cfgService.obtener()).thenReturn(cfg);
		InscripcionParticipanteRepository repo = mock(InscripcionParticipanteRepository.class);
		when(repo.contarOcupados(-1L)).thenReturn(3L);

		DisponibilidadService.Resumen r = new DisponibilidadService(cfgService, repo, clock).consultar();

		assertThat(r.hoy()).isEqualTo(LocalDate.of(2026, 10, 28));
		assertThat(r.ocupados()).isEqualTo(3);
		assertThat(r.disponibles()).isEqualTo(57);
	}

}
