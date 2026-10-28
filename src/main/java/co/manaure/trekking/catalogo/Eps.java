package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** EPS; solo Otra pide el nombre. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "eps")
public class Eps {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "pide_nombre", nullable = false)
	private boolean pideNombre;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected Eps() {
	}

	public String getClave() { return clave; }
	public boolean isPideNombre() { return pideNombre; }
	public short getOrden() { return orden; }

}
