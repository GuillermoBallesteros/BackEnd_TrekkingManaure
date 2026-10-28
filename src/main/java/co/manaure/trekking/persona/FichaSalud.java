package co.manaure.trekking.persona;

import co.manaure.trekking.cifrado.CifradoSaludConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Ficha de salud 1:1 con la persona. Los detalles se guardan cifrados (AES-256-GCM) en las columnas
 * {@code *_cifrada}; las banderas {@code tiene_*} van en claro y se derivan de si hay detalle. Nunca se
 * incluyen valores de salud en {@link #toString()}.
 */
@Entity
@Table(name = "ficha_salud")
public class FichaSalud {

	@Id
	@Column(name = "persona_id")
	private Long personaId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "persona_id")
	private Persona persona;

	@Column(name = "tiene_enfermedad", nullable = false)
	private boolean tieneEnfermedad;

	@Column(name = "tiene_alergias", nullable = false)
	private boolean tieneAlergias;

	@Column(name = "tiene_discapacidad", nullable = false)
	private boolean tieneDiscapacidad;

	@Convert(converter = CifradoSaludConverter.class)
	@Column(name = "enfermedad_cifrada", columnDefinition = "text")
	private String enfermedad;

	@Convert(converter = CifradoSaludConverter.class)
	@Column(name = "alergias_cifrada", columnDefinition = "text")
	private String alergias;

	@Convert(converter = CifradoSaludConverter.class)
	@Column(name = "discapacidad_cifrada", columnDefinition = "text")
	private String discapacidad;

	@Column(name = "practica_deporte", nullable = false)
	private boolean practicaDeporte;

	protected FichaSalud() {
	}

	/** Un detalle nulo significa "no tiene": la bandera correspondiente queda en falso. */
	public FichaSalud(Persona persona, String enfermedad, String alergias, String discapacidad,
			boolean practicaDeporte) {
		this.persona = persona;
		this.enfermedad = enfermedad;
		this.alergias = alergias;
		this.discapacidad = discapacidad;
		this.tieneEnfermedad = enfermedad != null;
		this.tieneAlergias = alergias != null;
		this.tieneDiscapacidad = discapacidad != null;
		this.practicaDeporte = practicaDeporte;
	}

	public Long getPersonaId() { return personaId; }
	public Persona getPersona() { return persona; }
	public boolean isTieneEnfermedad() { return tieneEnfermedad; }
	public boolean isTieneAlergias() { return tieneAlergias; }
	public boolean isTieneDiscapacidad() { return tieneDiscapacidad; }
	public String getEnfermedad() { return enfermedad; }
	public String getAlergias() { return alergias; }
	public String getDiscapacidad() { return discapacidad; }
	public boolean isPracticaDeporte() { return practicaDeporte; }

	@Override
	public String toString() {
		return "FichaSalud[personaId=" + personaId + ", datos de salud omitidos]";
	}

}
