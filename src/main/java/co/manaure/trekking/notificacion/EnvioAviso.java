package co.manaure.trekking.notificacion;

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
 * Aviso por correo reclamado para una inscripción (HU-N15, diseño §3.7). Las filas se crean con
 * {@link EnvioAvisoRepository#insertarSiNoExiste} para que la restricción única decida quién envía.
 */
@Entity
@Table(name = "envio_aviso")
public class EnvioAviso {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "inscripcion_id", nullable = false)
	private Long inscripcionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "tipo", nullable = false, length = 20)
	private TipoAviso tipo;

	@Column(name = "referencia", nullable = false, length = 40)
	private String referencia;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 10)
	private EstadoEnvio estado;

	@Column(name = "intentos", nullable = false)
	private int intentos;

	@Column(name = "mensaje_id", length = 120)
	private String mensajeId;

	@Column(name = "creado_en", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime creadoEn;

	@Column(name = "actualizado_en", nullable = false, insertable = false)
	private OffsetDateTime actualizadoEn;

	protected EnvioAviso() {
	}

	public Long getId() { return id; }
	public Long getInscripcionId() { return inscripcionId; }
	public TipoAviso getTipo() { return tipo; }
	public String getReferencia() { return referencia; }
	public EstadoEnvio getEstado() { return estado; }
	public int getIntentos() { return intentos; }
	public String getMensajeId() { return mensajeId; }
	public OffsetDateTime getCreadoEn() { return creadoEn; }
	public OffsetDateTime getActualizadoEn() { return actualizadoEn; }

}
