package co.manaure.trekking.api.dto;

import tools.jackson.databind.JsonNode;

/** Cuerpo de {@code PUT /api/v1/admin/fecha-cierre}. {@code fechaCierre} llega crudo: solo vale un texto AAAA-MM-DD. */
public record FechaCierreRequest(JsonNode fechaCierre) {
}
