package co.manaure.trekking.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.SecurityConfig;

/** Valida la cadena de seguridad real (JWT) con un controlador admin mínimo que solo existe en pruebas. */
@Import({ SecurityConfig.class, JwtConfig.class, AccesoAdminTest.AdminPruebaController.class })
@TestPropertySource(properties = "app.seguridad.jwt-secret=clave-jwt-solo-para-pruebas-0123456789")
@WebMvcTest(AccesoAdminTest.AdminPruebaController.class)
class AccesoAdminTest {

	@TestComponent
	@RestController
	@RequestMapping("/api/v1/admin/prueba")
	static class AdminPruebaController {
		@GetMapping
		String ping() {
			return "ok";
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	JwtEncoder encoder;

	private String token(Instant emision, Instant expira, List<String> roles) {
		return token(encoder, emision, expira, roles);
	}

	private static String token(JwtEncoder encoder, Instant emision, Instant expira, List<String> roles) {
		JwtClaimsSet claims = JwtClaimsSet.builder().subject("1").claim("email", "admin@ejemplo.com")
				.claim("roles", roles).issuedAt(emision).expiresAt(expira).build();
		return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
				.getTokenValue();
	}

	@Test
	void tokenConFirmaInvalidaDa401() throws Exception {
		// Firmado con otro secreto (32 bytes) pero con claims correctos y vigentes.
		JwtEncoder ajeno = new JwtConfig("otro-secreto-distinto-0123456789-abcdef").jwtEncoder();
		Instant ahora = Instant.now();
		String t = token(ajeno, ahora, ahora.plusSeconds(900), List.of("ADMIN"));
		mvc.perform(get("/api/v1/admin/prueba").header("Authorization", "Bearer " + t))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void rutasPublicasNoExigenToken() throws Exception {
		// Sin controladores en esta prueba: la seguridad deja pasar la petición (no responde 401 ni 403).
		for (var peticion : List.of(get("/api/v1/config"), get("/api/v1/disponibilidad"),
				post("/api/v1/reservas"), post("/api/v1/auth/login"), get("/actuator/health"))) {
			assertThat(mvc.perform(peticion).andReturn().getResponse().getStatus()).isNotIn(401, 403);
		}
	}

	@Test
	void unaRutaNoDeclaradaSeDeniegaAunConTokenAdmin() throws Exception {
		mvc.perform(get("/api/v1/otra-cosa")).andExpect(status().isUnauthorized());
		Instant ahora = Instant.now();
		String t = token(ahora, ahora.plusSeconds(900), List.of("ADMIN"));
		mvc.perform(get("/api/v1/otra-cosa").header("Authorization", "Bearer " + t))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
	}

	@Test
	void sinTokenDa401() throws Exception {
		mvc.perform(get("/api/v1/admin/prueba"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void tokenVencidoDa401() throws Exception {
		Instant ahora = Instant.now();
		String t = token(ahora.minusSeconds(9 * 3600), ahora.minusSeconds(3600), List.of("ADMIN"));
		mvc.perform(get("/api/v1/admin/prueba").header("Authorization", "Bearer " + t))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("NO_AUTENTICADO"));
	}

	@Test
	void tokenBasuraDa401() throws Exception {
		mvc.perform(get("/api/v1/admin/prueba").header("Authorization", "Bearer abc.def.ghi"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenVigenteConRolAdminDa200() throws Exception {
		Instant ahora = Instant.now();
		String t = token(ahora, ahora.plusSeconds(3600), List.of("ADMIN"));
		mvc.perform(get("/api/v1/admin/prueba").header("Authorization", "Bearer " + t))
				.andExpect(status().isOk());
	}

	@Test
	void tokenSinRolAdminDa403() throws Exception {
		Instant ahora = Instant.now();
		String t = token(ahora, ahora.plusSeconds(3600), List.of("OTRO"));
		mvc.perform(get("/api/v1/admin/prueba").header("Authorization", "Bearer " + t))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ACCESO_DENEGADO"));
	}

}
