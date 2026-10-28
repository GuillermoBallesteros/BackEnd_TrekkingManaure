package co.manaure.trekking.catalogo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Parentesco del contacto de emergencia. Catálogo de solo lectura (clave natural en texto, igual que el front). */
@Entity
@Immutable
@Table(name = "parentesco")
public class Parentesco {

	@Id
	@Column(name = "clave", nullable = false)
	private String clave;

	@Column(name = "es_responsable_legal", nullable = false)
	private boolean esResponsableLegal;

	@Column(name = "orden", nullable = false)
	private short orden;

	protected Parentesco() {
	}

	public String getClave() { return clave; }
	public boolean isEsResponsableLegal() { return esResponsableLegal; }
	public short getOrden() { return orden; }

}
