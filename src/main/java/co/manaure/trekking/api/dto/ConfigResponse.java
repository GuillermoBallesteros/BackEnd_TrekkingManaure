package co.manaure.trekking.api.dto;

import java.time.LocalDate;
import java.util.List;

import co.manaure.trekking.configuracion.ConfiguracionEvento;

/** Diseño §6.2: {@code GET /api/v1/config}. Sin datos de personas ni de administradores. */
public record ConfigResponse(Evento evento, Precios precios, String versionTerminos) {

	public record Evento(String nombre, List<LocalDate> fechas, LocalDate fechaAperturaInscripciones,
			LocalDate fechaCierre) {
	}

	public record Precios(String moneda, long total, long inscripcion, long kitCompleto, long acompanante,
			long acompananteConKit, long cuatrimoto, long parapente, long hotelNoche) {
	}

	public static final String MONEDA = "COP";

	public static ConfigResponse desde(ConfiguracionEvento c) {
		return new ConfigResponse(
				new Evento(c.getNombreEvento(), List.of(c.getFechaEventoInicio(), c.getFechaEventoFin()),
						c.getFechaApertura(), c.getFechaCierre()),
				new Precios(MONEDA, c.getPrecioTitular(), c.getValorApartado(), c.getKitCompleto(),
						c.getPrecioAcompananteSinKit(), c.getPrecioAcompananteConKit(), c.getPrecioCuatrimoto(),
						c.getPrecioParapente(), c.getPrecioHotelNoche()),
				c.getVersionTerminos());
	}

}
