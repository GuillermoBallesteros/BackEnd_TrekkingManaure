package co.manaure.trekking.persona;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Contacto de emergencia 1:1 con la persona. */
@Entity
@Table(name = "contacto_emergencia")
public class ContactoEmergencia {

	@Id
	@Column(name = "persona_id")
	private Long personaId;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@MapsId
	@JoinColumn(name = "persona_id")
	private Persona persona;

	@Column(name = "nombre", nullable = false, length = 120)
	private String nombre;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "telefono_pais", nullable = false, length = 2, columnDefinition = "char(2)")
	private String telefonoPais;

	@Column(name = "telefono_numero", nullable = false, length = 15)
	private String telefonoNumero;

	@Column(name = "parentesco", nullable = false, length = 40)
	private String parentesco;

	protected ContactoEmergencia() {
	}

	public ContactoEmergencia(Persona persona, String nombre, String telefonoPais, String telefonoNumero,
			String parentesco) {
		this.persona = persona;
		this.nombre = nombre;
		this.telefonoPais = telefonoPais;
		this.telefonoNumero = telefonoNumero;
		this.parentesco = parentesco;
	}

	public Long getPersonaId() { return personaId; }
	public Persona getPersona() { return persona; }
	public String getNombre() { return nombre; }
	public String getTelefonoPais() { return telefonoPais; }
	public String getTelefonoNumero() { return telefonoNumero; }
	public String getParentesco() { return parentesco; }

	@Override
	public String toString() {
		return "ContactoEmergencia[personaId=" + personaId + "]";
	}

}
