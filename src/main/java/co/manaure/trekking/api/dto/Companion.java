package co.manaure.trekking.api.dto;

import co.manaure.trekking.api.dto.TiposReserva.Commitments;
import co.manaure.trekking.api.dto.TiposReserva.Consent;
import co.manaure.trekking.api.dto.TiposReserva.EmergencyContact;
import co.manaure.trekking.api.dto.TiposReserva.Eps;
import co.manaure.trekking.api.dto.TiposReserva.Health;
import co.manaure.trekking.api.dto.TiposReserva.Phone;

/** Acompañante en la respuesta: {@code Person} aplanada más {@code kit} y {@code shirtSize} (nula sin kit). */
public record Companion(
		String firstName, String lastName, String documentType, String document, String birthDate, String gender,
		Phone phone, String email, String city, Eps eps, EmergencyContact emergencyContact, Health health,
		Commitments commitments, Consent consent, String stay, boolean kit, String shirtSize) {

	@Override
	public String toString() {
		return "Companion[***]";
	}

}
