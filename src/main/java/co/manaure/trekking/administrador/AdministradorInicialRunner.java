package co.manaure.trekking.administrador;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.seguridad.ValidadorCredenciales;

/**
 * Crea el administrador inicial desde ADMIN_EMAIL y ADMIN_PASSWORD si la tabla está vacía y ambos son válidos.
 * Si no, advierte (sin imprimir los valores) y la aplicación arranca igual. Nunca cambia una clave existente.
 */
@Component
public class AdministradorInicialRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdministradorInicialRunner.class);

	private final AdministradorRepository repositorio;
	private final PasswordEncoder encoder;
	private final String correo;
	private final String clave;

	public AdministradorInicialRunner(AdministradorRepository repositorio, PasswordEncoder encoder,
			@Value("${app.admin.correo:}") String correo, @Value("${app.admin.clave:}") String clave) {
		this.repositorio = repositorio;
		this.encoder = encoder;
		this.correo = correo;
		this.clave = clave;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (repositorio.count() > 0) {
			return;
		}
		boolean correoValido = ValidadorCredenciales.validarCorreo(correo).isEmpty();
		boolean claveValida = ValidadorCredenciales.validarClave(clave).isEmpty();
		if (!correoValido || !claveValida) {
			log.warn("No se creó el administrador inicial: ADMIN_EMAIL {} y ADMIN_PASSWORD {} "
					+ "(correo con formato válido; clave de {} a {} caracteres sin espacios en los extremos).",
					correoValido ? "es válido" : "falta o no es válido",
					claveValida ? "es válida" : "falta o no es válida", ValidadorCredenciales.CLAVE_MIN,
					ValidadorCredenciales.CLAVE_MAX);
			return;
		}
		repositorio.save(new Administrador(ValidadorCredenciales.recortar(correo).toLowerCase(Locale.ROOT),
				encoder.encode(clave)));
		log.info("Administrador inicial creado.");
	}

}
