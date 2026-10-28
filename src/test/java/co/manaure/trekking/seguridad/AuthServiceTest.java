package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import co.manaure.trekking.administrador.Administrador;
import co.manaure.trekking.administrador.AdministradorRepository;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.seguridad.dto.LoginRequest;

/** Reglas del servicio con dependencias simuladas; el flujo contra PostgreSQL está en LoginIntegrationTest. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	static final Instant AHORA = Instant.parse("2026-10-28T15:20:00Z");
	static final String IP = "10.0.0.1";
	static final String HASH_FICTICIO = "$2a$12$hash-ficticio";

	@Mock
	AdministradorRepository administradores;
	@Mock
	IntentoLoginRepository intentos;
	@Mock
	PasswordEncoder encoder;
	@Mock
	JwtService jwtService;
	@Mock
	RefreshTokenService refreshService;
	@Mock
	AuditoriaService auditoria;

	AuthService servicio;

	private IntentoLogin parConFallos(int fallos, Instant bloqueadoHasta) {
		try {
			var ctor = IntentoLogin.class.getDeclaredConstructor();
			ctor.setAccessible(true);
			IntentoLogin par = ctor.newInstance();
			for (int i = 0; i < fallos; i++) {
				par.registrarFallo(1000, AHORA, java.time.Duration.ofMinutes(15));
			}
			if (bloqueadoHasta != null) {
				var campo = IntentoLogin.class.getDeclaredField("bloqueadoHasta");
				campo.setAccessible(true);
				campo.set(par, bloqueadoHasta);
			}
			return par;
		}
		catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
	}

	@BeforeEach
	void crear() {
		when(encoder.encode(anyString())).thenReturn(HASH_FICTICIO);
		servicio = new AuthService(administradores, intentos, encoder, jwtService, refreshService, auditoria,
				Clock.fixed(AHORA, ZoneOffset.UTC), 5, 15, 50);
	}

	@Test
	void correoInexistenteInvocaElVerificadorContraElHashFicticioYDa401() {
		when(administradores.findByCorreo("nadie@ejemplo.com")).thenReturn(Optional.empty());
		when(intentos.findWithLockByIpAndCorreoHash(eq(IP), anyString())).thenReturn(Optional.of(parConFallos(0, null)));

		assertThatThrownBy(() -> servicio.login(new LoginRequest("Nadie@Ejemplo.com", "clave-cualquiera", false), IP))
				.isInstanceOfSatisfying(NegocioException.class, ex -> {
					assertThat(ex.getCode()).isEqualTo(CodigoError.CREDENCIALES_INVALIDAS);
					assertThat(ex.getMessage()).isEqualTo("Correo o contraseña incorrectos.");
				});

		verify(encoder).matches("clave-cualquiera", HASH_FICTICIO);
		// El correo inexistente se cuenta igual que el existente.
		verify(intentos).asegurar(eq(IP), anyString(), any());
		verify(auditoria, never()).registrar(any(), any(), any(), any(), any());
	}

	@Test
	void claveIncorrectaDaElMismoMensajeQueElCorreoInexistente() {
		Administrador admin = new Administrador("admin@ejemplo.com", "$2a$12$real");
		when(administradores.findByCorreo("admin@ejemplo.com")).thenReturn(Optional.of(admin));
		when(encoder.matches("clave-incorrecta", "$2a$12$real")).thenReturn(false);
		when(intentos.findWithLockByIpAndCorreoHash(eq(IP), anyString())).thenReturn(Optional.of(parConFallos(0, null)));

		assertThatThrownBy(() -> servicio.login(new LoginRequest("admin@ejemplo.com", "clave-incorrecta", null), IP))
				.isInstanceOfSatisfying(NegocioException.class, ex -> {
					assertThat(ex.getCode()).isEqualTo(CodigoError.CREDENCIALES_INVALIDAS);
					assertThat(ex.getMessage()).isEqualTo("Correo o contraseña incorrectos.");
				});
		verify(intentos).asegurar(eq(IP), anyString(), any());
	}

	@Test
	void parBloqueadoDa429SinVerificarLaClaveRealPeroEjecutaElHashFicticio() {
		when(intentos.findByIpAndCorreoHash(eq(IP), anyString()))
				.thenReturn(Optional.of(parConFallos(5, AHORA.plusSeconds(600))));

		assertThatThrownBy(() -> servicio.login(new LoginRequest("admin@ejemplo.com", "clave-correcta-1", false), IP))
				.isInstanceOfSatisfying(IntentosExcedidosException.class, ex -> {
					assertThat(ex.getCode()).isEqualTo(CodigoError.INTENTOS_EXCEDIDOS);
					assertThat(ex.getReintentarEnSegundos()).isEqualTo(600);
				});

		verify(encoder).matches("clave-correcta-1", HASH_FICTICIO);
		verify(administradores, never()).findByCorreo(any());
		verify(encoder, never()).matches(eq("clave-correcta-1"), eq("$2a$12$real"));
	}

	@Test
	void parBloqueadoParaCorreoInexistenteDaElMismo429() {
		when(intentos.findByIpAndCorreoHash(eq(IP), anyString()))
				.thenReturn(Optional.of(parConFallos(5, AHORA.plusSeconds(600))));

		assertThatThrownBy(() -> servicio.login(new LoginRequest("nadie@ejemplo.com", "clave-cualquiera", false), IP))
				.isInstanceOfSatisfying(IntentosExcedidosException.class,
						ex -> assertThat(ex.getReintentarEnSegundos()).isEqualTo(600));
		verify(encoder).matches("clave-cualquiera", HASH_FICTICIO);
	}

	@Test
	void elQuintoFalloDeLaParejaAuditaBloqueoSinCorreoNiClave() {
		Administrador admin = new Administrador("admin@ejemplo.com", "$2a$12$real");
		when(administradores.findByCorreo("admin@ejemplo.com")).thenReturn(Optional.of(admin));
		when(encoder.matches("mala-clave-1", "$2a$12$real")).thenReturn(false);
		when(intentos.findWithLockByIpAndCorreoHash(eq(IP), anyString())).thenReturn(Optional.of(parConFallos(4, null)));

		assertThatThrownBy(() -> servicio.login(new LoginRequest("admin@ejemplo.com", "mala-clave-1", false), IP))
				.isInstanceOf(NegocioException.class);

		org.mockito.ArgumentCaptor<String> ref = org.mockito.ArgumentCaptor.forClass(String.class);
		org.mockito.ArgumentCaptor<String> detalle = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(auditoria).registrar(any(), eq(co.manaure.trekking.auditoria.AccionAuditoria.BLOQUEO_CUENTA), any(),
				ref.capture(), detalle.capture());
		assertThat(ref.getValue() + detalle.getValue()).doesNotContain("@").doesNotContain("mala-clave-1");
	}

	@Test
	void alAlcanzarElTopeGlobalSoloSeAuditaYNoSeRechazaOtraCosa() {
		when(administradores.findByCorreo("nadie@ejemplo.com")).thenReturn(Optional.empty());
		when(intentos.findWithLockByIpAndCorreoHash(eq(IP), anyString())).thenReturn(Optional.of(parConFallos(0, null)));
		when(intentos.fallosDesde(anyString(), any())).thenReturn(50L);

		assertThatThrownBy(() -> servicio.login(new LoginRequest("nadie@ejemplo.com", "clave-cualquiera", false), IP))
				.isInstanceOfSatisfying(NegocioException.class,
						ex -> assertThat(ex.getCode()).isEqualTo(CodigoError.CREDENCIALES_INVALIDAS));
		verify(auditoria).registrar(any(), eq(co.manaure.trekking.auditoria.AccionAuditoria.BLOQUEO_CUENTA), any(),
				eq("LOGIN-GLOBAL"), anyString());
	}

	@Test
	void datosConFormatoInvalidoDan400ConCamposYNoTocanLaBase() {
		assertThatThrownBy(() -> servicio.login(new LoginRequest("no-es-correo", "corta", true), IP))
				.isInstanceOfSatisfying(NegocioException.class, ex -> {
					assertThat(ex.getCode()).isEqualTo(CodigoError.DATOS_INVALIDOS);
					assertThat(ex.getMessage()).isEqualTo("Revisa los datos marcados.");
					assertThat(ex.getProps()).containsKey("campos");
					@SuppressWarnings("unchecked")
					Map<String, String> campos = (Map<String, String>) ex.getProps().get("campos");
					assertThat(campos).containsOnlyKeys("correo", "clave");
				});

		verify(administradores, never()).findByCorreo(any());
		verify(encoder, never()).matches(any(), eq(HASH_FICTICIO));
	}

	@Test
	void unaSolicitudSinCuerpoDa400() {
		assertThatThrownBy(() -> servicio.login(null, IP)).isInstanceOfSatisfying(NegocioException.class,
				ex -> assertThat(ex.getCode()).isEqualTo(CodigoError.DATOS_INVALIDOS));
	}

}
