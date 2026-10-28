package co.manaure.trekking.catalogo;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** País con indicativo y reglas de celular y fijo. Catálogo de solo lectura. */
@Entity
@Immutable
@Table(name = "pais_telefono")
public class PaisTelefono {

	@Id
	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "iso", nullable = false, length = 2, columnDefinition = "char(2)")
	private String iso;

	@Column(name = "nombre", nullable = false)
	private String nombre;

	@Column(name = "indicativo", nullable = false)
	private String indicativo;

	@Column(name = "ejemplo", nullable = false)
	private String ejemplo;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "movil_largos", nullable = false, columnDefinition = "smallint[]")
	private Short[] movilLargos;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "movil_inicios", nullable = false, columnDefinition = "varchar(3)[]")
	private String[] movilInicios;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "fijo_largos", columnDefinition = "smallint[]")
	private Short[] fijoLargos;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "fijo_inicios", columnDefinition = "varchar(3)[]")
	private String[] fijoInicios;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected PaisTelefono() {
	}

	public String getIso() { return iso; }
	public String getNombre() { return nombre; }
	public String getIndicativo() { return indicativo; }
	public String getEjemplo() { return ejemplo; }
	public Short[] getMovilLargos() { return movilLargos; }
	public String[] getMovilInicios() { return movilInicios; }
	/** Nulo si el país no admite teléfono fijo. */
	public Short[] getFijoLargos() { return fijoLargos; }
	public String[] getFijoInicios() { return fijoInicios; }
	public short getOrden() { return orden; }
	public boolean admiteFijo() { return fijoLargos != null; }

}
