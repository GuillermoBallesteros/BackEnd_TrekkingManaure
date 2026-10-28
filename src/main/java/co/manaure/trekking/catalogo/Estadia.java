package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Estadía; solo Páramo ocupa cupo. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "estadia")
public class Estadia {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "ocupa_cupo", nullable = false)
	private boolean ocupaCupo;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected Estadia() {
	}

	public String getClave() { return clave; }
	public boolean isOcupaCupo() { return ocupaCupo; }
	public short getOrden() { return orden; }

}
