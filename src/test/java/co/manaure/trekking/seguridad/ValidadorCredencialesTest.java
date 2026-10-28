package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Mismos casos y textos que validaciones.ts del panel. */
class ValidadorCredencialesTest {

	@Test
	void correoConTextosDelFront() {
		assertThat(ValidadorCredenciales.validarCorreo("")).contains("Escribe tu correo.");
		assertThat(ValidadorCredenciales.validarCorreo("   ")).contains("Escribe tu correo.");
		String formato = "Escribe un correo válido, por ejemplo nombre@dominio.com.";
		assertThat(ValidadorCredenciales.validarCorreo("sin-arroba")).contains(formato);
		assertThat(ValidadorCredenciales.validarCorreo("a@b")).contains(formato);
		assertThat(ValidadorCredenciales.validarCorreo("a@b.c")).contains(formato);
		assertThat(ValidadorCredenciales.validarCorreo("a b@c.co")).contains(formato);
		assertThat(ValidadorCredenciales.validarCorreo("a".repeat(250) + "@b.co")).contains(formato);
		assertThat(ValidadorCredenciales.validarCorreo("a".repeat(248) + "@b.co")).isEmpty();
		assertThat(ValidadorCredenciales.validarCorreo("  admin@ejemplo.com ")).isEmpty();
	}

	@Test
	void claveConTextosDelFront() {
		assertThat(ValidadorCredenciales.validarClave("")).contains("Escribe tu contraseña.");
		assertThat(ValidadorCredenciales.validarClave(" clave-valida")).contains(
				"La contraseña no puede empezar ni terminar con espacios.");
		assertThat(ValidadorCredenciales.validarClave("clave-valida ")).contains(
				"La contraseña no puede empezar ni terminar con espacios.");
		assertThat(ValidadorCredenciales.validarClave("1234567")).contains(
				"La contraseña debe tener al menos 8 caracteres.");
		assertThat(ValidadorCredenciales.validarClave("12345678")).isEmpty();
		assertThat(ValidadorCredenciales.validarClave("a".repeat(128))).isEmpty();
		assertThat(ValidadorCredenciales.validarClave("a".repeat(129))).contains(
				"La contraseña no puede superar 128 caracteres.");
		assertThat(ValidadorCredenciales.validarClave("con espacio dentro")).isEmpty();
	}

	@Test
	void nombreDesdeCorreo() {
		assertThat(ValidadorCredenciales.nombreDesdeCorreo("maria.perez@ejemplo.com")).isEqualTo("Maria Perez");
		assertThat(ValidadorCredenciales.nombreDesdeCorreo("ADMIN@ejemplo.com")).isEqualTo("Admin");
		assertThat(ValidadorCredenciales.nombreDesdeCorreo("juan_de-la.cruz@x.co")).isEqualTo("Juan De La Cruz");
		assertThat(ValidadorCredenciales.nombreDesdeCorreo("  ana@x.co ")).isEqualTo("Ana");
	}

}
