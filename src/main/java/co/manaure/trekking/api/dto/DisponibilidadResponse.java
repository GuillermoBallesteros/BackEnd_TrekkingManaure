package co.manaure.trekking.api.dto;

import java.time.LocalDate;

/**
 * Diseño §6.2: {@code GET /api/v1/disponibilidad}. {@code capacity}, {@code occupied} y {@code available} son los
 * cupos de la carrera (una reserva gasta uno); {@code paramo} son los cupos del páramo (uno por persona).
 */
public record DisponibilidadResponse(int capacity, int occupied, int available, CuposResponse paramo,
		LocalDate fechaCierre, LocalDate today) {
}
