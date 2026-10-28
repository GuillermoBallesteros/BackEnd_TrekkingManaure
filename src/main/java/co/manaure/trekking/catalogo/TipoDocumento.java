package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Tipo de documento con patrón (sin anclas) y edades admitidas. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "tipo_documento")
public class TipoDocumento {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "patron", nullable = false)
	private String patron;

	@Column(name = "ayuda", nullable = false)
	private String ayuda;

	@Column(name = "edad_min")
	private Short edadMin;

	@Column(name = "edad_max")
	private Short edadMax;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected TipoDocumento() {
	}

	public String getClave() { return clave; }
	public String getPatron() { return patron; }
	public String getAyuda() { return ayuda; }
	public Short getEdadMin() { return edadMin; }
	public Short getEdadMax() { return edadMax; }
	public short getOrden() { return orden; }

}
