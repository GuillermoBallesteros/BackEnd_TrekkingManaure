package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Talla de camisa. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "talla")
public class Talla {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected Talla() {
	}

	public String getClave() { return clave; }
	public short getOrden() { return orden; }

}
