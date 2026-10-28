package co.manaure.trekking.api.dto;

/** Cuerpo de {@code PUT /api/v1/admin/pagos/{id}/revision}: "Aprobado", "Rechazado" o "Por revisar" y el motivo. */
public record RevisionPagoRequest(@jakarta.validation.constraints.Size(max = 50) String review,
		@jakarta.validation.constraints.Size(max = 1000) String reason) {
}
