package co.manaure.trekking.pago;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import co.manaure.trekking.reserva.Inscripcion;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Pago de una inscripción (llave Bre-B, sin pasarela). Monto en pesos enteros. */
@Entity
@Table(name = "pago")
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** PAG-0001; es el id del JSON. */
	@Column(name = "codigo", nullable = false, length = 12, updatable = false)
	private String codigo;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inscripcion_id", nullable = false)
	private Inscripcion inscripcion;

	@Enumerated(EnumType.STRING)
	@Column(name = "concepto", nullable = false, length = 20)
	private ConceptoPago concepto;

	@Column(name = "monto", nullable = false)
	private long monto;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Enumerated(EnumType.STRING)
	@Column(name = "revision", nullable = false, length = 12)
	private RevisionPago revision;

	@Column(name = "motivo_rechazo", length = 200)
	private String motivoRechazo;

	@Enumerated(EnumType.STRING)
	@Column(name = "origen", nullable = false, length = 10)
	private OrigenPago origen;

	@Column(name = "registrado_por")
	private Long registradoPor;

	@Column(name = "revisado_por")
	private Long revisadoPor;

	@Column(name = "revisado_en")
	private OffsetDateTime revisadoEn;

	/** Desde cuándo está (o estuvo por última vez) en "Por revisar"; la caducidad automática parte de aquí. */
	@Column(name = "en_revision_desde", nullable = false)
	private OffsetDateTime enRevisionDesde;

	@Column(name = "creado_en", insertable = false, updatable = false)
	private OffsetDateTime creadoEn;

	protected Pago() {
	}

	/** Pago público nuevo: nace POR_REVISAR, sin revisor ni motivo. */
	public static Pago publico(String codigo, Inscripcion inscripcion, ConceptoPago concepto, long monto, LocalDate fecha,
			OffsetDateTime ahora) {
		Pago p = new Pago();
		p.codigo = codigo;
		p.inscripcion = inscripcion;
		p.concepto = concepto;
		p.monto = monto;
		p.fecha = fecha;
		p.revision = RevisionPago.POR_REVISAR;
		p.origen = OrigenPago.PUBLICO;
		p.enRevisionDesde = ahora;
		return p;
	}

	/** Pago manual (HU-N09): nace verificado, con quien lo registró como revisor. */
	public static Pago manual(String codigo, Inscripcion inscripcion, ConceptoPago concepto, long monto,
			LocalDate fecha, Long administradorId, OffsetDateTime ahora) {
		Pago p = new Pago();
		p.codigo = codigo;
		p.inscripcion = inscripcion;
		p.concepto = concepto;
		p.monto = monto;
		p.fecha = fecha;
		p.revision = RevisionPago.APROBADO;
		p.origen = OrigenPago.MANUAL;
		p.registradoPor = administradorId;
		p.revisadoPor = administradorId;
		p.revisadoEn = ahora;
		p.enRevisionDesde = ahora;
		return p;
	}

	/**
	 * Aplica una revisión (HU-N08): al aprobar o rechazar guarda revisor y momento; al volver a Por revisar los
	 * borra. El motivo solo existe mientras está Rechazado.
	 */
	public void revisar(RevisionPago nueva, String motivo, Long revisor, OffsetDateTime ahora) {
		this.revision = nueva;
		this.motivoRechazo = nueva == RevisionPago.RECHAZADO ? motivo : null;
		boolean revisado = nueva != RevisionPago.POR_REVISAR;
		this.revisadoPor = revisado ? revisor : null;
		this.revisadoEn = revisado ? ahora : null;
		if (nueva == RevisionPago.POR_REVISAR) {
			this.enRevisionDesde = ahora;
		}
	}

	public Long getId() { return id; }
	public String getCodigo() { return codigo; }
	public Inscripcion getInscripcion() { return inscripcion; }
	public ConceptoPago getConcepto() { return concepto; }
	public long getMonto() { return monto; }
	public LocalDate getFecha() { return fecha; }
	public RevisionPago getRevision() { return revision; }
	public String getMotivoRechazo() { return motivoRechazo; }
	public OrigenPago getOrigen() { return origen; }
	public Long getRegistradoPor() { return registradoPor; }
	public Long getRevisadoPor() { return revisadoPor; }
	public OffsetDateTime getRevisadoEn() { return revisadoEn; }
	public OffsetDateTime getEnRevisionDesde() { return enRevisionDesde; }
	public OffsetDateTime getCreadoEn() { return creadoEn; }

}
