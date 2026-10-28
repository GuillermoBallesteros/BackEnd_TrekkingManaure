package co.manaure.trekking.api.dto;

import java.time.LocalDate;
import java.util.List;

/** Diseño §6.4: {@code GET /api/v1/admin/reservas}. Contiene datos de salud: no se registra en logs. */
public record AdminReservas(List<Registration> registrations, int capacity, LocalDate fechaCierre, LocalDate today) {

	@Override
	public String toString() {
		return "AdminReservas[" + registrations.size() + " reservas]";
	}

}
