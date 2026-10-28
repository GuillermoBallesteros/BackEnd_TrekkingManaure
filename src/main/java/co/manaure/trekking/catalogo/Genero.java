package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Género. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "genero")
public class Genero {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected Genero() {
	}

	public String getClave() { return clave; }
	public short getOrden() { return orden; }

}
