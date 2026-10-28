package co.manaure.trekking.api.dto;

import java.time.LocalDate;

/** Diseño §6.2: {@code GET /api/v1/disponibilidad}. */
public record DisponibilidadResponse(int capacity, int occupied, int available, LocalDate fechaCierre,
		LocalDate today) {
}
