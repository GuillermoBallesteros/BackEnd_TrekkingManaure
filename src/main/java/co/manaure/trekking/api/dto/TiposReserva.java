package co.manaure.trekking.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Tipos anidados del contrato de reservas (diseño §6.1), con los mismos nombres que {@code shared/reservas/tipos.ts}.
 * Sirven de entrada y de salida. Los booleanos de entrada son {@code Boolean} para que un valor ausente llegue
 * como nulo a las reglas (422 con su mensaje) y no se confunda con {@code false}. Ningún {@code toString()}
 * muestra valores: son datos personales o de salud y no deben llegar a los logs.
 */
public final class TiposReserva {

	/**
	 * Topes de longitud de entrada (hallazgo H5): holgados respecto de las reglas de negocio (60, 120, 200...) para que
	 * un valor apenas excedido siga recibiendo su error de regla (422), pero un texto enorme se rechace con 400
	 * antes de cualquier regex. MAX_EXTRAS: el catálogo tiene 3 actividades extra.
	 */
	public static final int MAX_TEXTO = 500;
	public static final int MAX_CODIGO = 100;
	public static final int MAX_EXTRAS = 3;

	private TiposReserva() {
	}

	public record Phone(@Size(max = MAX_CODIGO) String country, @Size(max = MAX_CODIGO) String number) {
		@Override
		public String toString() {
			return "Phone[***]";
		}
	}

	public record Eps(@Size(max = MAX_CODIGO) String entity, @Size(max = MAX_TEXTO) String other) {
		@Override
		public String toString() {
			return "Eps[***]";
		}
	}

	public record EmergencyContact(@Size(max = MAX_TEXTO) String name, @NotNull @Valid Phone phone,
			@Size(max = MAX_CODIGO) String relationship) {
		@Override
		public String toString() {
			return "EmergencyContact[***]";
		}
	}

	public record Health(@Size(max = MAX_TEXTO) String illness, @Size(max = MAX_TEXTO) String allergies,
			@Size(max = MAX_TEXTO) String disability, Boolean exercises) {
		@Override
		public String toString() {
			return "Health[datos de salud omitidos]";
		}
	}

	public record Commitments(Boolean protectedAreas, Boolean environment) {
	}

	public record Consent(@Size(max = MAX_CODIGO) String version, @Size(max = MAX_CODIGO) String acceptedAt,
			Boolean imageUse) {
		@Override
		public String toString() {
			return "Consent[***]";
		}
	}

	public record PreEventHotel(@Size(max = MAX_CODIGO) String checkIn, @Size(max = MAX_CODIGO) String checkOut,
			@NotNull Boolean withCompanion) {
		@Override
		public String toString() {
			return "PreEventHotel[***]";
		}
	}

	public record Payment(String id, String concept, long amount, String date, String review,
			String rejectionReason) {
	}

}
