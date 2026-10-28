package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.Optional;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.modelo.AcompananteRegla;
import co.manaure.trekking.regla.modelo.PersonaRegla;

/** Acompañante (persona, talla y kit) y distinción frente al titular. */
public final class ValidadorAcompanante {

	private ValidadorAcompanante() {
	}

	/** Persona con las mismas reglas que el titular, más talla según kit ({@code validateCompanion}). */
	public static Optional<ErrorRegla> validar(AcompananteRegla acompanante, LocalDate hoy,
			CatalogoReglas catalogos, ParametrosEvento evento) {
		Optional<ErrorRegla> persona = ValidadorPersona.validar(acompanante.persona(), Quien.ACOMPANANTE, hoy,
				catalogos, evento);
		if (persona.isPresent()) {
			return persona;
		}
		if (acompanante.kit() && !catalogos.esTalla(acompanante.talla())) {
			return ErrorRegla.de(CodigoError.TALLA_INVALIDA, "Elige la talla de camisa del acompañante.",
					"companion.shirtSize");
		}
		if (!acompanante.kit() && acompanante.talla() != null) {
			return ErrorRegla.de(CodigoError.TALLA_INVALIDA, "El acompañante sin kit no lleva talla de camisa.",
					"companion.shirtSize");
		}
		return Optional.empty();
	}

	/** Datos que no pueden repetirse entre el titular y su acompañante ({@code validateDistinct}). */
	public static Optional<ErrorRegla> distinto(PersonaRegla titular, PersonaRegla acompanante,
			CatalogoReglas catalogos) {
		if (Textos.normalizarDocumento(acompanante.documento())
				.equals(Textos.normalizarDocumento(titular.documento()))) {
			return repetido("El acompañante no puede tener el mismo documento que el participante.",
					"companion.document");
		}
		if (Textos.nz(acompanante.correo()).equals(Textos.nz(titular.correo()))) {
			return repetido("El acompañante debe tener un correo distinto al del participante.", "companion.email");
		}
		String telefonoTitular = ValidadorTelefono.phoneKey(titular.telefono(), catalogos);
		String telefonoAcompanante = ValidadorTelefono.phoneKey(acompanante.telefono(), catalogos);
		if (telefonoAcompanante.equals(telefonoTitular)) {
			return repetido("El acompañante debe tener un celular distinto al del participante.",
					"companion.phone.number");
		}
		// El contacto de emergencia debe quedarse en casa: no puede ser alguien que va al evento.
		if (ValidadorTelefono.phoneKey(titular.contacto().telefono(), catalogos).equals(telefonoAcompanante)) {
			return repetido("El contacto de emergencia del participante no puede ser el acompañante.",
					"emergencyContact.phone.number");
		}
		if (ValidadorTelefono.phoneKey(acompanante.contacto().telefono(), catalogos).equals(telefonoTitular)) {
			return repetido("El contacto de emergencia del acompañante no puede ser el participante.",
					"companion.emergencyContact.phone.number");
		}
		if (Textos.nombreComparable(titular.contacto().nombre())
				.equals(Textos.nombreCompletoComparable(acompanante.nombres(), acompanante.apellidos()))) {
			return repetido("El contacto de emergencia del participante no puede ser el acompañante.",
					"emergencyContact.name");
		}
		if (Textos.nombreComparable(acompanante.contacto().nombre())
				.equals(Textos.nombreCompletoComparable(titular.nombres(), titular.apellidos()))) {
			return repetido("El contacto de emergencia del acompañante no puede ser el participante.",
					"companion.emergencyContact.name");
		}
		return Optional.empty();
	}

	private static Optional<ErrorRegla> repetido(String detalle, String campo) {
		return ErrorRegla.de(CodigoError.ACOMPANANTE_REPETIDO, detalle, campo);
	}

}
