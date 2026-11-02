package co.manaure.trekking.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de {@code PUT /api/v1/admin/reservas/{id}/plazo}. {@code days} llega crudo para distinguir un entero de un
 * decimal, un texto o un valor ausente (todos dan 422 PLAZO_INVALIDO, sin coerciones silenciosas).
 */
public record PlazoRequest(JsonNode days) {

	/** Los días si es un entero JSON que cabe en un int; si no, {@code null}. */
	public Integer diasEnteros() {
		return days != null && days.isIntegralNumber() && days.canConvertToInt() ? days.intValue() : null;
	}

}
