package co.manaure.trekking.api.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Diseño §6.4: {@code GET /api/v1/admin/reservas}. Contiene datos de salud: no se registra en logs.
 * {@code capacity} y {@code occupied} son los cupos de la carrera; {@code paramo}, los cupos del páramo.
 */
public record AdminReservas(List<Registration> registrations, int capacity, int occupied, CuposResponse paramo,
		LocalDate fechaCierre, LocalDate today) {

	@Override
	public String toString() {
		return "AdminReservas[" + registrations.size() + " reservas]";
	}

}
