package co.manaure.trekking.reserva;

import co.manaure.trekking.persona.Persona;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Titular o acompañante de una inscripción. {@code documentoActivo} copia el documento mientras la inscripción
 * esté activa y es nulo si está Rechazada: su índice único es la barrera final de RN-06. Lo mantiene el servicio.
 */
@Entity
@Table(name = "inscripcion_participante")
public class InscripcionParticipante {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inscripcion_id", nullable = false)
	private Inscripcion inscripcion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "persona_id", nullable = false)
	private Persona persona;

	@Enumerated(EnumType.STRING)
	@Column(name = "rol", nullable = false, length = 12)
	private RolParticipante rol;

	@Column(name = "estadia", nullable = false, length = 30)
	private String estadia;

	@Column(name = "lleva_kit", nullable = false)
	private boolean llevaKit;

	@Column(name = "talla", length = 5)
	private String talla;

	@Column(name = "documento_activo", length = 20)
	private String documentoActivo;

	protected InscripcionParticipante() {
	}

	public InscripcionParticipante(Inscripcion inscripcion, Persona persona, RolParticipante rol, String estadia,
			boolean llevaKit, String talla, String documentoActivo) {
		this.inscripcion = inscripcion;
		this.persona = persona;
		this.rol = rol;
		this.estadia = estadia;
		this.llevaKit = llevaKit;
		this.talla = talla;
		this.documentoActivo = documentoActivo;
	}

	/** Lo mantiene el servicio: el documento mientras la inscripción esté activa, nulo si está Rechazada. */
	public void setDocumentoActivo(String documentoActivo) { this.documentoActivo = documentoActivo; }

	public Long getId() { return id; }
	public Inscripcion getInscripcion() { return inscripcion; }
	public Persona getPersona() { return persona; }
	public RolParticipante getRol() { return rol; }
	public String getEstadia() { return estadia; }
	public boolean isLlevaKit() { return llevaKit; }
	public String getTalla() { return talla; }
	public String getDocumentoActivo() { return documentoActivo; }

}
