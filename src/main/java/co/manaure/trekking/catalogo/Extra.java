package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Actividad extra. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "extra")
public class Extra {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "etiqueta", nullable = false)
	private String etiqueta;

	@Column(name = "requiere_acompanante", nullable = false)
	private boolean requiereAcompanante;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected Extra() {
	}

	public String getClave() { return clave; }
	public String getEtiqueta() { return etiqueta; }
	public boolean isRequiereAcompanante() { return requiereAcompanante; }
	public short getOrden() { return orden; }

}
