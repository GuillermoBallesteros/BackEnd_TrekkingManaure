package co.manaure.trekking.api.dto;

import co.manaure.trekking.api.dto.TiposReserva.Commitments;
import co.manaure.trekking.api.dto.TiposReserva.Consent;
import co.manaure.trekking.api.dto.TiposReserva.EmergencyContact;
import co.manaure.trekking.api.dto.TiposReserva.Eps;
import co.manaure.trekking.api.dto.TiposReserva.Health;
import co.manaure.trekking.api.dto.TiposReserva.Phone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Acompañante de {@link NewRegistration}: {@code Person} aplanada más {@code kit} y {@code shirtSize}. */
public record NewCompanion(
		@Size(max = TiposReserva.MAX_TEXTO) String firstName, @Size(max = TiposReserva.MAX_TEXTO) String lastName,
		@Size(max = TiposReserva.MAX_CODIGO) String documentType, @Size(max = TiposReserva.MAX_CODIGO) String document,
		@Size(max = TiposReserva.MAX_CODIGO) String birthDate, @Size(max = TiposReserva.MAX_CODIGO) String gender,
		@NotNull @Valid Phone phone, @Size(max = TiposReserva.MAX_TEXTO) String email,
		@Size(max = TiposReserva.MAX_TEXTO) String city, @NotNull @Valid Eps eps,
		@NotNull @Valid EmergencyContact emergencyContact, @NotNull @Valid Health health,
		@NotNull @Valid Commitments commitments, @NotNull @Valid Consent consent, @Size(max = TiposReserva.MAX_CODIGO) String stay,
		@NotNull Boolean kit, @Size(max = TiposReserva.MAX_CODIGO) String shirtSize) {

	@Override
	public String toString() {
		return "NewCompanion[***]";
	}

}
