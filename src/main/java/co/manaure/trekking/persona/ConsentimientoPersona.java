package co.manaure.trekking.persona;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Aceptación de términos y compromisos de una persona (1:1). Los dos compromisos solo pueden ser verdaderos. */
@Entity
@Table(name = "consentimiento_persona")
public class ConsentimientoPersona {

	@Id
	@Column(name = "persona_id")
	private Long personaId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "persona_id")
	private Persona persona;

	@Column(name = "version_terminos", nullable = false, length = 30)
	private String versionTerminos;

	@Column(name = "aceptado_en", nullable = false)
	private OffsetDateTime aceptadoEn;

	@Column(name = "uso_imagen", nullable = false)
	private boolean usoImagen;

	@Column(name = "compromiso_areas_protegidas", nullable = false)
	private boolean compromisoAreasProtegidas;

	@Column(name = "compromiso_medio_ambiente", nullable = false)
	private boolean compromisoMedioAmbiente;

	protected ConsentimientoPersona() {
	}

	public ConsentimientoPersona(Persona persona, String versionTerminos, OffsetDateTime aceptadoEn, boolean usoImagen,
			boolean compromisoAreasProtegidas, boolean compromisoMedioAmbiente) {
		this.persona = persona;
		this.versionTerminos = versionTerminos;
		this.aceptadoEn = aceptadoEn;
		this.usoImagen = usoImagen;
		this.compromisoAreasProtegidas = compromisoAreasProtegidas;
		this.compromisoMedioAmbiente = compromisoMedioAmbiente;
	}

	public Long getPersonaId() { return personaId; }
	public Persona getPersona() { return persona; }
	public String getVersionTerminos() { return versionTerminos; }
	public OffsetDateTime getAceptadoEn() { return aceptadoEn; }
	public boolean isUsoImagen() { return usoImagen; }
	public boolean isCompromisoAreasProtegidas() { return compromisoAreasProtegidas; }
	public boolean isCompromisoMedioAmbiente() { return compromisoMedioAmbiente; }

}
