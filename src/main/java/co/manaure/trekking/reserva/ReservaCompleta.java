package co.manaure.trekking.reserva;

import java.util.List;

import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.persona.ConsentimientoPersona;
import co.manaure.trekking.persona.ContactoEmergencia;
import co.manaure.trekking.persona.FichaSalud;
import co.manaure.trekking.persona.Persona;

/**
 * Una reserva con todo lo que necesita el JSON {@code Registration}: lo usan la respuesta de crear y la lista
 * del panel. {@code acompanante} es nulo si no hay; {@code hotel} es nulo si no lo pidió; {@code extras} va en el
 * orden del catálogo; {@code pagos} por id ascendente.
 */
public record ReservaCompleta(Inscripcion inscripcion, Integrante titular, Integrante acompanante,
		List<String> extras, HotelPrevio hotel, List<Pago> pagos) {

	/** Participante con su persona, ficha de salud, contacto y consentimiento. */
	public record Integrante(InscripcionParticipante participante, Persona persona, FichaSalud ficha,
			ContactoEmergencia contacto, ConsentimientoPersona consentimiento) {
	}

}
