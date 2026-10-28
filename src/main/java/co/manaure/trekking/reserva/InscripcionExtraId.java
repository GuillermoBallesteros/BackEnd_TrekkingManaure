package co.manaure.trekking.reserva;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record InscripcionExtraId(
		@Column(name = "inscripcion_id") Long inscripcionId,
		@Column(name = "extra", length = 30) String extra) implements Serializable {
}
