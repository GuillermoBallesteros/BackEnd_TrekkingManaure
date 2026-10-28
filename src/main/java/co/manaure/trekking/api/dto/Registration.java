package co.manaure.trekking.api.dto;

import java.util.List;

import co.manaure.trekking.api.dto.TiposReserva.Commitments;
import co.manaure.trekking.api.dto.TiposReserva.Consent;
import co.manaure.trekking.api.dto.TiposReserva.EmergencyContact;
import co.manaure.trekking.api.dto.TiposReserva.Eps;
import co.manaure.trekking.api.dto.TiposReserva.Health;
import co.manaure.trekking.api.dto.TiposReserva.Payment;
import co.manaure.trekking.api.dto.TiposReserva.Phone;
import co.manaure.trekking.api.dto.TiposReserva.PreEventHotel;

/**
 * Reserva completa tal como la guarda y la lee el front (diseño §6.1), con {@code total} adicional. Incluye la
 * ficha de salud descifrada: solo se devuelve en las respuestas permitidas por el diseño §10.4.
 */
public record Registration(
		String id, String createdAt, String firstName, String lastName, String documentType, String document,
		String birthDate, String gender, Phone phone, String email, String city, Eps eps,
		EmergencyContact emergencyContact, Health health, Commitments commitments, Consent consent, String stay,
		String shirtSize, Companion companion, List<String> extras, PreEventHotel preEventHotel, String plan,
		String commitmentDate, boolean guardianAuthorized, List<Payment> payments, long total) {

	@Override
	public String toString() {
		return "Registration[***]";
	}

}
