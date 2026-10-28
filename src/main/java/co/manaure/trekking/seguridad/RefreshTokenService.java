package co.manaure.trekking.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite, rota y revoca refresh tokens de 256 bits; en base solo queda su SHA-256 (diseño §10.1).
 * Rotación de un solo uso con detección de reutilización. Los resultados negativos se devuelven como valor
 * (no como excepción) para que la transacción que revoca la familia nunca se deshaga.
 */
@Service
public class RefreshTokenService {

	/** Token en claro (solo para la cookie), su familia y vencimiento. */
	public record RefreshEmitido(String token, UUID familia, Instant expiraEn) {

		@Override
		public String toString() {
			return "RefreshEmitido[familia=" + familia + ", expiraEn=" + expiraEn + "]";
		}
	}

	public enum Estado {
		/** Rotado: hay refresh nuevo. */
		ROTADO,
		/** Inexistente, vencido o revocado. */
		INVALIDO,
		/** Ya usado fuera de la ventana de gracia: se revocó toda la familia. */
		REUTILIZADO,
		/** Ya usado hace un instante (carrera legítima): se pide reintentar, sin revocar. */
		EN_CURSO
	}

	/** Resultado de {@link #rotar}. {@code emitido}, {@code recordada} e {@code iniciadaEn} solo con ROTADO. */
	public record Rotacion(Estado estado, Long administradorId, UUID familia, RefreshEmitido emitido,
			boolean recordada, Instant iniciadaEn) {

		@Override
		public String toString() {
			return "Rotacion[estado=" + estado + "]";
		}

		static Rotacion sin(Estado estado) {
			return new Rotacion(estado, null, null, null, false, null);
		}
	}

	private static final int BYTES = 32;

	private final SesionRefreshRepository repositorio;
	private final SecureRandom aleatorio = new SecureRandom();
	private final Duration duracionRecordada;
	private final Duration duracionSinRecordar;
	private final Duration gracia;

	public RefreshTokenService(SesionRefreshRepository repositorio,
			@Value("${app.seguridad.sesion-recordada-dias:7}") long recordadaDias,
			@Value("${app.seguridad.sesion-horas:8}") long sesionHoras,
			@Value("${app.seguridad.refresh-gracia-segundos:0}") long graciaSegundos) {
		this.repositorio = repositorio;
		this.duracionRecordada = Duration.ofDays(recordadaDias);
		this.duracionSinRecordar = Duration.ofHours(sesionHoras);
		this.gracia = Duration.ofSeconds(graciaSegundos);
	}

	/** Duración de la sesión: 7 días con "recordar", 8 horas sin él [SUPUESTO, configurable]. */
	public Duration duracion(boolean recordada) {
		return recordada ? duracionRecordada : duracionSinRecordar;
	}

	@Transactional
	public RefreshEmitido crearNuevaFamilia(long administradorId, boolean recordada, Instant ahora) {
		// Milisegundos exactos: iniciadaEn y sesionExpiraEn se leen de la base en cada rotación.
		Instant inicio = ahora.truncatedTo(ChronoUnit.MILLIS);
		return emitir(administradorId, UUID.randomUUID(), recordada, inicio, inicio.plus(duracion(recordada)));
	}

	/**
	 * Rota un refresh. Bloquea la fila del token (SELECT ... FOR UPDATE): dos solicitudes simultáneas con el mismo
	 * token se serializan y solo la primera obtiene un hijo; la segunda ve el token usado.
	 * <p>Esa segunda solicitud es una reutilización (revoca la familia) salvo que el token se haya usado hace menos
	 * de {@code app.seguridad.refresh-gracia-segundos} y su hijo siga sin usarse: entonces es la carrera de una
	 * sola pestaña y se responde EN_CURSO (409) sin revocar. Con gracia 0 (por defecto) no hay excepción.
	 */
	@Transactional
	public Rotacion rotar(String tokenPlano, Instant ahora) {
		if (tokenPlano == null || tokenPlano.isBlank()) {
			return Rotacion.sin(Estado.INVALIDO);
		}
		Optional<SesionRefresh> encontrado = repositorio.findWithLockByTokenHash(hash(tokenPlano));
		if (encontrado.isEmpty()) {
			return Rotacion.sin(Estado.INVALIDO);
		}
		SesionRefresh actual = encontrado.get();
		if (actual.getRevocadoEn() != null || !ahora.isBefore(actual.getExpiraEn())) {
			return Rotacion.sin(Estado.INVALIDO);
		}
		if (actual.getUsadoEn() != null) {
			if (enCarreraLegitima(actual, ahora)) {
				return new Rotacion(Estado.EN_CURSO, actual.getAdministradorId(), actual.getFamilia(), null, false, null);
			}
			repositorio.revocarFamilia(actual.getFamilia(), ahora);
			return new Rotacion(Estado.REUTILIZADO, actual.getAdministradorId(), actual.getFamilia(), null, false, null);
		}
		Guardado hijo = guardar(actual.getAdministradorId(), actual.getFamilia(), actual.isRecordada(), ahora,
				actual.getExpiraEn());
		actual.marcarUsado(ahora, hijo.id());
		Instant inicio = repositorio.findInicioDeFamilia(actual.getFamilia());
		return new Rotacion(Estado.ROTADO, actual.getAdministradorId(), actual.getFamilia(), hijo.emitido(),
				actual.isRecordada(), inicio);
	}

	private boolean enCarreraLegitima(SesionRefresh usado, Instant ahora) {
		if (gracia.isZero() || usado.getReemplazadoPor() == null
				|| ahora.isAfter(usado.getUsadoEn().plus(gracia))) {
			return false;
		}
		return repositorio.findById(usado.getReemplazadoPor())
				.map(hijo -> hijo.getUsadoEn() == null && hijo.getRevocadoEn() == null).orElse(false);
	}

	/** Revoca la familia del token presentado. Devuelve el administrador y la familia si el token existía. */
	@Transactional
	public Optional<SesionRefresh> cerrar(String tokenPlano, Instant ahora) {
		if (tokenPlano == null || tokenPlano.isBlank()) {
			return Optional.empty();
		}
		Optional<SesionRefresh> encontrado = repositorio.findByTokenHash(hash(tokenPlano));
		encontrado.ifPresent(s -> repositorio.revocarFamilia(s.getFamilia(), ahora));
		return encontrado;
	}

	/** Borra los refresh cuyo vencimiento es anterior al límite. */
	@Transactional
	public int borrarVencidosAntesDe(Instant limite) {
		return repositorio.borrarVencidosAntesDe(limite);
	}

	private record Guardado(RefreshEmitido emitido, Long id) {
	}

	private RefreshEmitido emitir(long administradorId, UUID familia, boolean recordada, Instant creadoEn,
			Instant expira) {
		return guardar(administradorId, familia, recordada, creadoEn, expira).emitido();
	}

	private Guardado guardar(long administradorId, UUID familia, boolean recordada, Instant creadoEn, Instant expira) {
		byte[] bytes = new byte[BYTES];
		aleatorio.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		SesionRefresh guardado = repositorio.saveAndFlush(new SesionRefresh(administradorId, familia, hash(token),
				recordada, creadoEn.truncatedTo(ChronoUnit.MILLIS), expira));
		return new Guardado(new RefreshEmitido(token, familia, expira), guardado.getId());
	}

	/** SHA-256 en hexadecimal minúscula (64 caracteres). */
	public static String hash(String token) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 no disponible", ex);
		}
	}

}
