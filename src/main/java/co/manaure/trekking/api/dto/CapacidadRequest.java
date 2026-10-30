package co.manaure.trekking.api.dto;

import tools.jackson.databind.JsonNode;

/**
 * Cuerpo de {@code PUT /api/v1/admin/capacidad}. {@code capacity} (cupos de la carrera) y {@code paramo} (cupos del
 * páramo) llegan crudos para no aceptar por coerción 10.0, "10" ni booleanos: solo vale un entero JSON.
 */
public record CapacidadRequest(JsonNode capacity, JsonNode paramo) {
}
