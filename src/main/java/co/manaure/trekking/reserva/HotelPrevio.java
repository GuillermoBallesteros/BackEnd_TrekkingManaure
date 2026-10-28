package co.manaure.trekking.reserva;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Hotel previo al evento (1:1 con la inscripción). */
@Entity
@Table(name = "hotel_previo")
public class HotelPrevio {

	@Id
	@Column(name = "inscripcion_id")
	private Long inscripcionId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "inscripcion_id")
	private Inscripcion inscripcion;

	@Column(name = "entrada", nullable = false)
	private LocalDate entrada;

	@Column(name = "salida", nullable = false)
	private LocalDate salida;

	@Column(name = "con_acompanante", nullable = false)
	private boolean conAcompanante;

	protected HotelPrevio() {
	}

	public HotelPrevio(Inscripcion inscripcion, LocalDate entrada, LocalDate salida, boolean conAcompanante) {
		this.inscripcion = inscripcion;
		this.entrada = entrada;
		this.salida = salida;
		this.conAcompanante = conAcompanante;
	}

	public Long getInscripcionId() { return inscripcionId; }
	public Inscripcion getInscripcion() { return inscripcion; }
	public LocalDate getEntrada() { return entrada; }
	public LocalDate getSalida() { return salida; }
	public boolean isConAcompanante() { return conAcompanante; }

}
