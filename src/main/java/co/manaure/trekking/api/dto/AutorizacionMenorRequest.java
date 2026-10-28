package co.manaure.trekking.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de {@code PUT /api/v1/admin/reservas/{id}/autorizacion-menor}. {@code received} llega crudo para rechazar
 * (400 DATOS_INVALIDOS) lo que no sea un booleano JSON, incluidos los textos "true"/"false".
 */
public record AutorizacionMenorRequest(JsonNode received) {

	public boolean esBooleano() {
		return received != null && received.isBoolean();
	}

}
