package co.manaure.trekking.persona;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Identidad y contacto de una persona. Una fila por persona por inscripción. Las claves de catálogo
 * (tipo de documento, género, país, EPS) son texto; la base impone las llaves foráneas.
 */
@Entity
@Table(name = "persona")
public class Persona {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombres", nullable = false, length = 60)
	private String nombres;

	@Column(name = "apellidos", nullable = false, length = 60)
	private String apellidos;

	@Column(name = "tipo_documento", nullable = false, length = 20)
	private String tipoDocumento;

	/** Guardado en mayúsculas. */
	@Column(name = "documento", nullable = false, length = 20)
	private String documento;

	@Column(name = "fecha_nacimiento", nullable = false)
	private LocalDate fechaNacimiento;

	@Column(name = "genero", nullable = false, length = 30)
	private String genero;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "telefono_pais", nullable = false, length = 2, columnDefinition = "char(2)")
	private String telefonoPais;

	@Column(name = "telefono_numero", nullable = false, length = 15)
	private String telefonoNumero;

	/** Guardado en minúsculas. */
	@Column(name = "correo", nullable = false, length = 254)
	private String correo;

	@Column(name = "ciudad", nullable = false, length = 60)
	private String ciudad;

	@Column(name = "eps", nullable = false, length = 60)
	private String eps;

	@Column(name = "eps_otra", length = 60)
	private String epsOtra;

	@Column(name = "creado_en", insertable = false, updatable = false)
	private OffsetDateTime creadoEn;

	protected Persona() {
	}

	public Persona(String nombres, String apellidos, String tipoDocumento, String documento, LocalDate fechaNacimiento,
			String genero, String telefonoPais, String telefonoNumero, String correo, String ciudad, String eps,
			String epsOtra) {
		this.nombres = nombres;
		this.apellidos = apellidos;
		this.tipoDocumento = tipoDocumento;
		this.documento = documento;
		this.fechaNacimiento = fechaNacimiento;
		this.genero = genero;
		this.telefonoPais = telefonoPais;
		this.telefonoNumero = telefonoNumero;
		this.correo = correo;
		this.ciudad = ciudad;
		this.eps = eps;
		this.epsOtra = epsOtra;
	}

	public Long getId() { return id; }
	public String getNombres() { return nombres; }
	public String getApellidos() { return apellidos; }
	public String getTipoDocumento() { return tipoDocumento; }
	public String getDocumento() { return documento; }
	public LocalDate getFechaNacimiento() { return fechaNacimiento; }
	public String getGenero() { return genero; }
	public String getTelefonoPais() { return telefonoPais; }
	public String getTelefonoNumero() { return telefonoNumero; }
	public String getCorreo() { return correo; }
	public String getCiudad() { return ciudad; }
	public String getEps() { return eps; }
	public String getEpsOtra() { return epsOtra; }
	public OffsetDateTime getCreadoEn() { return creadoEn; }

	/** Sin datos personales (documento, nombres, contacto). */
	@Override
	public String toString() {
		return "Persona[id=" + id + "]";
	}

}
