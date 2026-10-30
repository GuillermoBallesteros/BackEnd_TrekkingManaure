package co.manaure.trekking.api.dto;

/** Capacidad, ocupados y libres de un contador de cupos. */
public record CuposResponse(int capacity, int occupied, int available) {

	public static CuposResponse de(int capacidad, long ocupados) {
		int ocupadosEnteros = Math.toIntExact(ocupados);
		return new CuposResponse(capacidad, ocupadosEnteros, Math.max(0, capacidad - ocupadosEnteros));
	}

}
