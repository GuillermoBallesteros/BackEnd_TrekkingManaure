package co.manaure.trekking.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reloj de prueba en America/Bogota con instante fijable: por defecto 2026-10-28T10:20:11.482-05:00. */
class RelojPrueba extends Clock {

	static final Instant INICIAL = Instant.parse("2026-10-28T15:20:11.482Z");
	private static final ZoneId ZONA = ZoneId.of("America/Bogota");

	private volatile Instant instante = INICIAL;

	void fijar(Instant nuevo) {
		this.instante = nuevo;
	}

	void restablecer() {
		this.instante = INICIAL;
	}

	@Override
	public ZoneId getZone() {
		return ZONA;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(instante, zone);
	}

	@Override
	public Instant instant() {
		return instante;
	}

	@TestConfiguration
	static class Config {
		@Bean
		@Primary
		RelojPrueba relojPrueba() {
			return new RelojPrueba();
		}
	}

}
