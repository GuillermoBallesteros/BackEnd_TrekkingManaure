package co.manaure.trekking.seguridad;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class AutenticacionConfig {

	static final int COSTO_BCRYPT = 12;

	/** BCrypt costo 12 (diseño §10.2). */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(COSTO_BCRYPT);
	}

}
