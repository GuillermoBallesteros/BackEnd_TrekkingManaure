package co.manaure.trekking.administrador;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "administrador")
public class Administrador {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 254, unique = true)
	private String correo;

	@Column(name = "clave_hash", nullable = false, length = 100)
	private String claveHash;

	@Column(nullable = false)
	private boolean activo = true;

	@Column(name = "intentos_fallidos", nullable = false)
	private int intentosFallidos;

	@Column(name = "bloqueado_hasta")
	private Instant bloqueadoHasta;

	@Column(name = "ultimo_acceso_en")
	private Instant ultimoAccesoEn;

	@Column(name = "creado_en", insertable = false, updatable = false)
	private Instant creadoEn;

	protected Administrador() {
	}

	public Administrador(String correo, String claveHash) {
		this.correo = correo;
		this.claveHash = claveHash;
		this.activo = true;
	}

	/** Bloqueo vigente en el instante dado. */
	public boolean estaBloqueado(Instant ahora) {
		return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
	}

	/** Si el bloqueo ya venció, reinicia el contador para que el plazo cumplido empiece de cero. */
	public void liberarSiVencido(Instant ahora) {
		if (bloqueadoHasta != null && !ahora.isBefore(bloqueadoHasta)) {
			bloqueadoHasta = null;
			intentosFallidos = 0;
		}
	}

	/** Suma un fallo; al llegar a {@code maxIntentos} bloquea por {@code bloqueo}. Devuelve true si bloqueó. */
	public boolean registrarFallo(int maxIntentos, Instant ahora, Duration bloqueo) {
		intentosFallidos++;
		if (intentosFallidos >= maxIntentos) {
			bloqueadoHasta = ahora.plus(bloqueo);
			return true;
		}
		return false;
	}

	public void registrarAcceso(Instant ahora) {
		intentosFallidos = 0;
		bloqueadoHasta = null;
		ultimoAccesoEn = ahora;
	}

	public Long getId() { return id; }
	public String getCorreo() { return correo; }
	public String getClaveHash() { return claveHash; }
	public boolean isActivo() { return activo; }
	public int getIntentosFallidos() { return intentosFallidos; }
	public Instant getBloqueadoHasta() { return bloqueadoHasta; }
	public Instant getUltimoAccesoEn() { return ultimoAccesoEn; }
	public Instant getCreadoEn() { return creadoEn; }

}
