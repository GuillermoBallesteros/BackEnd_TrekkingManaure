package co.manaure.trekking.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de {@code POST /api/v1/admin/reservas/{id}/pagos}. {@code amount} llega crudo para distinguir un entero de
 * un decimal, un texto o un valor ausente (todos dan 422 MONTO_INVALIDO, sin coerciones silenciosas).
 */
public record PagoManualRequest(JsonNode amount) {

	/** El monto en pesos si es un entero JSON que cabe en un long; si no, {@code null}. */
	public Long montoEntero() {
		return amount != null && amount.isIntegralNumber() && amount.canConvertToLong() ? amount.longValue() : null;
	}

}
