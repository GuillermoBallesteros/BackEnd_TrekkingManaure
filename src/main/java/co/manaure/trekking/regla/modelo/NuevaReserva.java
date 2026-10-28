package co.manaure.trekking.regla.modelo;

import java.util.List;

/**
 * Modelo interno de {@code NewRegistration}. {@code tallaTitular} es la talla de camisa del titular;
 * {@code plan} es "total" o "apartado" (texto, se valida contra el catálogo de planes).
 */
public record NuevaReserva(PersonaRegla titular, String tallaTitular, AcompananteRegla acompanante,
		List<String> extras, HotelSolicitado hotel, String plan, String fechaCompromiso) {

	@Override
	public String toString() {
		return "NuevaReserva[***]";
	}

}
