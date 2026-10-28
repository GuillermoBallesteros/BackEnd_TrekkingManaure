package co.manaure.trekking.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.administrador.Administrador;
import co.manaure.trekking.administrador.AdministradorRepository;
import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.seguridad.JwtService.AccessToken;
import co.manaure.trekking.seguridad.RefreshTokenService.RefreshEmitido;
import co.manaure.trekking.seguridad.RefreshTokenService.Rotacion;
import co.manaure.trekking.seguridad.dto.LoginRequest;
import co.manaure.trekking.seguridad.dto.SesionResponse;

/**
 * Inicio de sesión (diseño §6.3 y §10). Los fallos y el bloqueo se cuentan por pareja (IP, correo) en la tabla
 * {@code intento_login}; el fallo se confirma aunque la solicitud termine en error (noRollbackFor).
 * Nunca registra en logs la clave, el hash ni el token.
 */
@Service
public class AuthService {

	/** Respuesta del login y el valor del refresh que el controlador pone en la cookie. */
	public record ResultadoLogin(SesionResponse sesion, String refreshToken, Optional<Duration> maxAgeCookie) {

		@Override
		public String toString() {
			return "ResultadoLogin[]";
		}
	}

	private static final String DETALLE_CREDENCIALES = "Correo o contraseña incorrectos.";

	private final AdministradorRepository administradores;
	private final IntentoLoginRepository intentos;
	private final PasswordEncoder encoder;
	private final JwtService jwtService;
	private final RefreshTokenService refreshService;
	private final AuditoriaService auditoria;
	private final Clock clock;
	private final int maxIntentos;
	private final Duration bloqueo;
	private final int topeGlobal;
	/** Hash del mismo costo que los reales: se compara cuando el correo no existe para igualar tiempos. */
	private final String hashFicticio;

	public AuthService(AdministradorRepository administradores, IntentoLoginRepository intentos, PasswordEncoder encoder, JwtService jwtService,
			RefreshTokenService refreshService, AuditoriaService auditoria, Clock clock,
			@Value("${app.seguridad.max-intentos:5}") int maxIntentos,
			@Value("${app.seguridad.bloqueo-minutos:15}") long bloqueoMinutos,
			@Value("${app.seguridad.tope-global-fallos:50}") int topeGlobal) {
		this.administradores = administradores;
		this.intentos = intentos;
		this.encoder = encoder;
		this.jwtService = jwtService;
		this.refreshService = refreshService;
		this.auditoria = auditoria;
		this.clock = clock;
		this.maxIntentos = maxIntentos;
		this.bloqueo = Duration.ofMinutes(bloqueoMinutos);
		this.topeGlobal = topeGlobal;
		this.hashFicticio = encoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(noRollbackFor = NegocioException.class)
	public ResultadoLogin login(LoginRequest request, String ip) {
		String correoRecibido = request == null ? null : request.correo();
		String clave = request == null ? null : request.clave();
		Map<String, String> errores = ValidadorCredenciales.validar(correoRecibido, clave);
		if (!errores.isEmpty()) {
			throw new NegocioException(CodigoError.DATOS_INVALIDOS, HttpStatus.BAD_REQUEST,
					"Revisa los datos marcados.", Map.of("campos", errores));
		}
		String correo = ValidadorCredenciales.recortar(correoRecibido).toLowerCase(Locale.ROOT);
		boolean recordar = Boolean.TRUE.equals(request.recordar());
		Instant ahora = clock.instant();
		String ipPar = ip == null || ip.isBlank() ? "desconocida" : (ip.length() > 64 ? ip.substring(0, 64) : ip);
		String correoHash = sha256(correo);

		// Pareja (IP, correo) bloqueada: 429 sin verificar la clave, pero tras una comparacion BCrypt contra el hash
		// ficticio para que el tiempo sea comparable. Se aplica igual exista o no la cuenta (no revela nada).
		Optional<IntentoLogin> par = intentos.findByIpAndCorreoHash(ipPar, correoHash);
		if (par.isPresent() && par.get().estaBloqueado(ahora)) {
			encoder.matches(clave, hashFicticio);
			long segundos = Math.max(1, (long) Math.ceil(Duration.between(ahora, par.get().getBloqueadoHasta()).toMillis() / 1000.0));
			throw new IntentosExcedidosException(segundos);
		}

		// Una cuenta inactiva se trata como inexistente: mismo camino, mismo tiempo y mismo conteo de fallos.
		Optional<Administrador> encontrado = administradores.findByCorreo(correo).filter(Administrador::isActivo);
		boolean correcta = encoder.matches(clave, encontrado.map(Administrador::getClaveHash).orElse(hashFicticio))
				&& encontrado.isPresent();
		if (!correcta) {
			registrarFallo(ipPar, correoHash, encontrado.map(Administrador::getId).orElse(null), ahora);
			throw credencialesInvalidas();
		}
		Administrador admin = encontrado.get();
		intentos.reiniciar(ipPar, correoHash);

		admin.registrarAcceso(ahora);
		long id = admin.getId();
		RefreshEmitido refresh = refreshService.crearNuevaFamilia(id, recordar, ahora);
		AccessToken acceso = jwtService.emitir(id, admin.getCorreo(), ahora);
		auditoria.registrar(id, AccionAuditoria.INICIAR_SESION, EntidadAuditoria.SESION, refresh.familia().toString(),
				"recordada=" + recordar);

		SesionResponse sesion = new SesionResponse(acceso.token(), "Bearer", admin.getCorreo(),
				ValidadorCredenciales.nombreDesdeCorreo(admin.getCorreo()), ahora.toEpochMilli(),
				acceso.expiraEn().toEpochMilli(), refresh.expiraEn().toEpochMilli(), recordar);
		return new ResultadoLogin(sesion, refresh.token(),
				recordar ? Optional.of(refreshService.duracion(true)) : Optional.empty());
	}

	/**
	 * Refresh rotatorio (diseño §10.1). Un solo uso: el token presentado queda usado y enlazado al nuevo, de la misma
	 * familia y con el mismo vencimiento. Un token ya usado revoca toda la familia y se audita SESION_REUTILIZADA.
	 * El fallo se confirma aunque la solicitud termine en error (noRollbackFor).
	 */
	@Transactional(noRollbackFor = NegocioException.class)
	public ResultadoLogin refrescar(String refreshToken) {
		Instant ahora = clock.instant();
		Rotacion rotacion = refreshService.rotar(refreshToken, ahora);
		switch (rotacion.estado()) {
			case INVALIDO -> throw new SesionInvalidaException();
			case REUTILIZADO -> {
				auditoria.registrar(rotacion.administradorId(), AccionAuditoria.SESION_REUTILIZADA,
						EntidadAuditoria.SESION, rotacion.familia().toString(), null);
				throw new SesionInvalidaException();
			}
			case EN_CURSO -> throw new NegocioException(CodigoError.RECURSO_OCUPADO, HttpStatus.CONFLICT,
					"Hay otra operación en curso. Intenta de nuevo.");
			case ROTADO -> {
			}
		}
		Optional<Administrador> admin = administradores.findById(rotacion.administradorId()).filter(Administrador::isActivo);
		if (admin.isEmpty()) {
			// Cuenta inactiva: se revoca la familia recién rotada y se responde como sesión inválida.
			refreshService.cerrar(rotacion.emitido().token(), ahora);
			throw new SesionInvalidaException();
		}
		AccessToken acceso = jwtService.emitir(admin.get().getId(), admin.get().getCorreo(), ahora);
		SesionResponse sesion = new SesionResponse(acceso.token(), "Bearer", admin.get().getCorreo(),
				ValidadorCredenciales.nombreDesdeCorreo(admin.get().getCorreo()), rotacion.iniciadaEn().toEpochMilli(),
				acceso.expiraEn().toEpochMilli(), rotacion.emitido().expiraEn().toEpochMilli(), rotacion.recordada());
		// La expiración no se extiende: la cookie dura lo que le queda a la sesión.
		return new ResultadoLogin(sesion, rotacion.emitido().token(), rotacion.recordada()
				? Optional.of(Duration.between(ahora, rotacion.emitido().expiraEn())) : Optional.empty());
	}

	/** Cierra la sesión: revoca la familia del refresh presentado. Idempotente (sin token o desconocido no falla). */
	@Transactional
	public void cerrarSesion(String refreshToken) {
		Instant ahora = clock.instant();
		refreshService.cerrar(refreshToken, ahora).ifPresent(s -> auditoria.registrar(s.getAdministradorId(),
				AccionAuditoria.CERRAR_SESION, EntidadAuditoria.SESION, s.getFamilia().toString(), null));
	}

	/**
	 * Cuenta el fallo de la pareja (exista o no la cuenta) y bloquea solo a esa pareja. Ademas vigila un tope global
	 * por cuenta (todas las IP): al alcanzarlo solo se audita; nunca se rechaza una clave correcta por ese motivo.
	 */
	private void registrarFallo(String ip, String correoHash, Long administradorId, Instant ahora) {
		intentos.asegurar(ip, correoHash, ahora);
		IntentoLogin par = intentos.findWithLockByIpAndCorreoHash(ip, correoHash).orElseThrow();
		if (par.registrarFallo(maxIntentos, ahora, bloqueo)) {
			auditoria.registrar(administradorId, AccionAuditoria.BLOQUEO_CUENTA, EntidadAuditoria.SESION, "LOGIN-PAR",
					"alcance=par;intentos=" + par.getFallos());
		}
		intentos.flush();
		long total = intentos.fallosDesde(correoHash, ahora.minus(bloqueo));
		if (total == topeGlobal) {
			auditoria.registrar(administradorId, AccionAuditoria.BLOQUEO_CUENTA, EntidadAuditoria.SESION, "LOGIN-GLOBAL",
					"alcance=global;intentos=" + total);
		}
	}

	private static String sha256(String texto) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static NegocioException credencialesInvalidas() {
		return new NegocioException(CodigoError.CREDENCIALES_INVALIDAS, HttpStatus.UNAUTHORIZED, DETALLE_CREDENCIALES);
	}

}
