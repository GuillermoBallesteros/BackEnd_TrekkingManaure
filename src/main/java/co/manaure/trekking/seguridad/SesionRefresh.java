package co.manaure.trekking.seguridad;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Un refresh token emitido. El token en claro nunca se guarda: solo su SHA-256 hexadecimal. */
@Entity
@Table(name = "sesion_refresh")
public class SesionRefresh {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "administrador_id", nullable = false, updatable = false)
	private Long administradorId;

	@Column(name = "familia", nullable = false, updatable = false)
	private UUID familia;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "char(64)", updatable = false)
	private String tokenHash;

	@Column(name = "recordada", nullable = false, updatable = false)
	private boolean recordada;

	/** Lo fija la aplicación (con su reloj) para que {@code iniciadaEn} sea exacto entre rotaciones. */
	@Column(name = "creado_en", nullable = false, updatable = false)
	private Instant creadoEn;

	@Column(name = "expira_en", nullable = false, updatable = false)
	private Instant expiraEn;

	@Column(name = "usado_en")
	private Instant usadoEn;

	@Column(name = "revocado_en")
	private Instant revocadoEn;

	@Column(name = "reemplazado_por")
	private Long reemplazadoPor;

	protected SesionRefresh() {
	}

	public SesionRefresh(Long administradorId, UUID familia, String tokenHash, boolean recordada, Instant creadoEn,
			Instant expiraEn) {
		this.administradorId = administradorId;
		this.familia = familia;
		this.tokenHash = tokenHash;
		this.recordada = recordada;
		this.creadoEn = creadoEn;
		this.expiraEn = expiraEn;
	}

	/** Marca este refresh como usado y lo enlaza con el que lo reemplaza. */
	public void marcarUsado(Instant ahora, Long reemplazoId) {
		this.usadoEn = ahora;
		this.reemplazadoPor = reemplazoId;
	}

	public Long getId() { return id; }
	public Long getAdministradorId() { return administradorId; }
	public UUID getFamilia() { return familia; }
	public String getTokenHash() { return tokenHash; }
	public boolean isRecordada() { return recordada; }
	public Instant getCreadoEn() { return creadoEn; }
	public Instant getExpiraEn() { return expiraEn; }
	public Instant getUsadoEn() { return usadoEn; }
	public Instant getRevocadoEn() { return revocadoEn; }
	public Long getReemplazadoPor() { return reemplazadoPor; }

}
