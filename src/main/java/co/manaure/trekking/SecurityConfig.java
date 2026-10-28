package co.manaure.trekking;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import co.manaure.trekking.seguridad.CabecerasSeguridadFilter;
import co.manaure.trekking.seguridad.LimiteCuerpoFilter;
import co.manaure.trekking.seguridad.RateLimitFilter;
import co.manaure.trekking.seguridad.TopeReservasPorIp;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import co.manaure.trekking.seguridad.ProblemAccessDeniedHandler;
import co.manaure.trekking.seguridad.ProblemAuthenticationEntryPoint;

/** JWT stateless (Bearer, sin CSRF). Rutas públicas del diseño §10.3; /api/v1/admin/** exige ROLE_ADMIN; el resto se deniega. */
@Configuration
@Import(CorsConfig.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
			@Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource, ObjectProvider<Clock> clockProvider,
			ObjectProvider<TopeReservasPorIp> topeProvider,
			@Value("${app.rate-limit.registro-por-minuto}") int limiteRegistro,
			@Value("${app.rate-limit.login-por-15-minutos}") int limiteLogin,
			@Value("${app.rate-limit.refresh-por-minuto}") int limiteRefresh,
			@Value("${app.limites.max-cuerpo-bytes:65536}") long maxCuerpoBytes,
			org.springframework.core.env.Environment entorno) throws Exception {
		// Swagger solo en el perfil dev; en prod springdoc además está desactivado (application.yml).
		boolean swaggerAbierto = entorno.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"));
		Clock clock = clockProvider.getIfAvailable(() -> Clock.system(ZoneId.of("America/Bogota")));
		ProblemAuthenticationEntryPoint entryPoint = new ProblemAuthenticationEntryPoint();

		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);

		return http
				// Las cabeceras de seguridad las escribe CabecerasSeguridadFilter (diseño §10.3), no el writer por defecto.
				.headers(AbstractHttpConfigurer::disable)
				.cors(c -> c.configurationSource(corsSource))
				.addFilterBefore(new CabecerasSeguridadFilter(), CorsFilter.class)
				.addFilterBefore(new RateLimitFilter(clock, limiteRegistro, limiteLogin, limiteRefresh,
						topeProvider.getIfAvailable()),
						UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(new LimiteCuerpoFilter(maxCuerpoBytes), UsernamePasswordAuthenticationFilter.class)
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> {
					if (swaggerAbierto) {
						auth.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
								.permitAll();
					}
					auth
						.requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/config").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/v1/disponibilidad").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/v1/reservas").permitAll()
						.requestMatchers("/api/v1/auth/**").permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.anyRequest().denyAll();
				})
				.oauth2ResourceServer(o -> o
						.jwt(j -> j.decoder(jwtDecoder).jwtAuthenticationConverter(converter))
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(new ProblemAccessDeniedHandler()))
				.exceptionHandling(e -> e
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(new ProblemAccessDeniedHandler()))
				.build();
	}

}
