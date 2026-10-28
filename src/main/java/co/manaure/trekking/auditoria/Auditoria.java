package co.manaure.trekking.auditoria;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Acción de administración. {@code detalle} solo lleva datos no personales (transición de estado, monto,
 * valor anterior y nuevo); nunca salud, nombres, documentos ni texto libre.
 */
@Entity
@Table(name = "auditoria")
public class Auditoria {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** Nulo en intentos fallidos sin usuario. */
	@Column(name = "administrador_id", updatable = false)
	private Long administradorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "accion", nullable = false, length = 40, updatable = false)
	private AccionAuditoria accion;

	@Enumerated(EnumType.STRING)
	@Column(name = "entidad", nullable = false, length = 30, updatable = false)
	private EntidadAuditoria entidad;

	@Column(name = "entidad_ref", nullable = false, length = 40, updatable = false)
	private String entidadRef;

	@Column(name = "detalle", length = 300, updatable = false)
	private String detalle;

	@Column(name = "fecha", insertable = false, updatable = false)
	private OffsetDateTime fecha;

	protected Auditoria() {
	}

	public Auditoria(Long administradorId, AccionAuditoria accion, EntidadAuditoria entidad, String entidadRef,
			String detalle) {
		this.administradorId = administradorId;
		this.accion = accion;
		this.entidad = entidad;
		this.entidadRef = entidadRef;
		this.detalle = detalle;
	}

	public Long getId() { return id; }
	public Long getAdministradorId() { return administradorId; }
	public AccionAuditoria getAccion() { return accion; }
	public EntidadAuditoria getEntidad() { return entidad; }
	public String getEntidadRef() { return entidadRef; }
	public String getDetalle() { return detalle; }
	public OffsetDateTime getFecha() { return fecha; }

}
