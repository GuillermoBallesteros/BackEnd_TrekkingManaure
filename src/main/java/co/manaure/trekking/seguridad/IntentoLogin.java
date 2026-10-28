package co.manaure.trekking.seguridad;

import java.time.Duration;
import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fallos de login de una pareja (IP, SHA-256 del correo normalizado). No guarda correos ni claves. */
@Entity
@Table(name = "intento_login")
public class IntentoLogin {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String ip;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "correo_hash", nullable = false, length = 64, columnDefinition = "char(64)")
	private String correoHash;

	@Column(nullable = false)
	private int fallos;

	@Column(name = "ultimo_fallo_en", nullable = false)
	private Instant ultimoFalloEn;

	@Column(name = "bloqueado_hasta")
	private Instant bloqueadoHasta;

	protected IntentoLogin() {
	}

	public boolean estaBloqueado(Instant ahora) {
		return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
	}

	/**
	 * Suma un fallo. Un bloqueo vencido, o fallos viejos (el último hace más de {@code bloqueo}), reinician el
	 * contador. Al llegar a {@code max} bloquea por {@code bloqueo}. Devuelve true si bloqueó.
	 */
	public boolean registrarFallo(int max, Instant ahora, Duration bloqueo) {
		boolean bloqueoVencido = bloqueadoHasta != null && !ahora.isBefore(bloqueadoHasta);
		boolean viejo = ultimoFalloEn != null && ultimoFalloEn.plus(bloqueo).isBefore(ahora);
		if (bloqueoVencido || viejo) {
			bloqueadoHasta = null;
			fallos = 0;
		}
		fallos++;
		ultimoFalloEn = ahora;
		if (fallos >= max) {
			bloqueadoHasta = ahora.plus(bloqueo);
			return true;
		}
		return false;
	}

	public int getFallos() { return fallos; }
	public Instant getBloqueadoHasta() { return bloqueadoHasta; }

}
