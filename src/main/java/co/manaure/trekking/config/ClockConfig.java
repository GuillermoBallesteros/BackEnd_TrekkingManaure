package co.manaure.trekking.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class ClockConfig {

	static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Bogota");

	@Bean
	Clock clock() {
		return Clock.system(ZONA_NEGOCIO);
	}

}
