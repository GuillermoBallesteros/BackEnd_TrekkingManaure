package co.manaure.trekking.reserva;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Actividad extra elegida en una inscripción. */
@Entity
@Table(name = "inscripcion_extra")
public class InscripcionExtra {

	@EmbeddedId
	private InscripcionExtraId id;

	protected InscripcionExtra() {
	}

	public InscripcionExtra(Long inscripcionId, String extra) {
		this.id = new InscripcionExtraId(inscripcionId, extra);
	}

	public InscripcionExtraId getId() { return id; }

}
