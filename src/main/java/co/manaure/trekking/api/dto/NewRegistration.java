package co.manaure.trekking.api.dto;

import java.util.List;

import co.manaure.trekking.api.dto.TiposReserva.Commitments;
import co.manaure.trekking.api.dto.TiposReserva.Consent;
import co.manaure.trekking.api.dto.TiposReserva.EmergencyContact;
import co.manaure.trekking.api.dto.TiposReserva.Eps;
import co.manaure.trekking.api.dto.TiposReserva.Health;
import co.manaure.trekking.api.dto.TiposReserva.Phone;
import co.manaure.trekking.api.dto.TiposReserva.PreEventHotel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Entrada de {@code POST /api/v1/reservas} (diseño §6.1): {@code Registration} sin {@code id}, {@code createdAt},
 * {@code payments}, {@code guardianAuthorized} ni {@code total}; si el cliente los envía se ignoran. Bean Validation
 * cubre solo la estructura (paso 1 del §8): objetos obligatorios presentes. Las reglas de negocio las valida
 * {@code ValidadorReserva}.
 */
public record NewRegistration(
		@Size(max = TiposReserva.MAX_NOMBRE) String firstName, @Size(max = TiposReserva.MAX_NOMBRE) String lastName,
		@Size(max = TiposReserva.MAX_CODIGO) String documentType, @Size(max = TiposReserva.MAX_CODIGO) String document,
		@Size(max = TiposReserva.MAX_CODIGO) String birthDate, @Size(max = TiposReserva.MAX_CODIGO) String gender,
		@NotNull @Valid Phone phone, @Size(max = TiposReserva.MAX_CORREO) String email,
		@Size(max = TiposReserva.MAX_NOMBRE) String city, @NotNull @Valid Eps eps,
		@NotNull @Valid EmergencyContact emergencyContact, @NotNull @Valid Health health,
		@NotNull @Valid Commitments commitments, @NotNull @Valid Consent consent, @Size(max = TiposReserva.MAX_CODIGO) String stay,
		@Size(max = TiposReserva.MAX_CODIGO) String shirtSize, @Valid NewCompanion companion,
		@NotNull @Size(max = TiposReserva.MAX_EXTRAS) List<@NotNull @Size(max = TiposReserva.MAX_CODIGO) String> extras,
		@Valid PreEventHotel preEventHotel, @NotNull @Size(max = TiposReserva.MAX_CODIGO) String plan,
		@Size(max = TiposReserva.MAX_CODIGO) String commitmentDate) {

	@Override
	public String toString() {
		return "NewRegistration[***]";
	}

}
