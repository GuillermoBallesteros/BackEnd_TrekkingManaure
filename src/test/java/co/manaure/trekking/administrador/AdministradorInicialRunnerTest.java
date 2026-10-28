package co.manaure.trekking.administrador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith({ MockitoExtension.class, OutputCaptureExtension.class })
class AdministradorInicialRunnerTest {

	@Mock
	AdministradorRepository repositorio;

	PasswordEncoder encoder = new BCryptPasswordEncoder(12);

	@Test
	void conVariablesValidasYTablaVaciaCreaAdministradorConCorreoEnMinusculasYBCryptCosto12() {
		when(repositorio.count()).thenReturn(0L);

		new AdministradorInicialRunner(repositorio, encoder, "Admin@Ejemplo.COM", "clave-secreta").run(null);

		ArgumentCaptor<Administrador> captor = ArgumentCaptor.forClass(Administrador.class);
		verify(repositorio).save(captor.capture());
		Administrador a = captor.getValue();
		assertThat(a.getCorreo()).isEqualTo("admin@ejemplo.com");
		assertThat(a.getClaveHash()).startsWith("$2a$12$").doesNotContain("clave-secreta");
		assertThat(encoder.matches("clave-secreta", a.getClaveHash())).isTrue();
	}

	@Test
	void siFaltanOSonInvalidasNoCreaNadaYAdvierteSinElValor(CapturedOutput salida) {
		when(repositorio.count()).thenReturn(0L);

		new AdministradorInicialRunner(repositorio, encoder, "", "").run(null);
		new AdministradorInicialRunner(repositorio, encoder, "admin@ejemplo.com", "corta").run(null);
		new AdministradorInicialRunner(repositorio, encoder, "no-es-correo", "clave-secreta-larga").run(null);
		new AdministradorInicialRunner(repositorio, encoder, "admin@ejemplo.com", " clave-con-espacio").run(null);

		verify(repositorio, never()).save(any());
		assertThat(salida.getOut() + salida.getErr()).contains("No se creó el administrador inicial")
				.doesNotContain("corta").doesNotContain("no-es-correo").doesNotContain("clave-secreta-larga")
				.doesNotContain("clave-con-espacio").doesNotContain("admin@ejemplo.com");
	}

	@Test
	void siYaExisteUnAdministradorNoCreaOtroNiCambiaLaClave() {
		when(repositorio.count()).thenReturn(1L);

		new AdministradorInicialRunner(repositorio, encoder, "otro@ejemplo.com", "otra-clave-nueva").run(null);

		verify(repositorio, never()).save(any());
	}

}
