package co.manaure.trekking.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.api.dto.CapacidadRequest;
import co.manaure.trekking.api.dto.ConfiguracionAdminResponses.Capacidad;
import co.manaure.trekking.api.dto.ConfiguracionAdminResponses.FechaCierre;
import co.manaure.trekking.api.dto.FechaCierreRequest;
import co.manaure.trekking.configuracion.ConfiguracionAdminService;

/** HU-N11 y HU-N12 (diseño §6.4). La seguridad de {@code /api/v1/admin/**} la aplica SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
public class ConfiguracionAdminController {

	private final ConfiguracionAdminService servicio;

	public ConfiguracionAdminController(ConfiguracionAdminService servicio) {
		this.servicio = servicio;
	}

	@PutMapping("/capacidad")
	public Capacidad fijarCapacidad(@RequestBody(required = false) CapacidadRequest cuerpo,
			@AuthenticationPrincipal Jwt jwt) {
		return servicio.fijarCapacidad(cuerpo == null ? null : cuerpo.capacity(),
				cuerpo == null ? null : cuerpo.paramo(), Long.valueOf(jwt.getSubject()));
	}

	@PutMapping("/fecha-cierre")
	public FechaCierre fijarFechaCierre(@RequestBody(required = false) FechaCierreRequest cuerpo,
			@AuthenticationPrincipal Jwt jwt) {
		return new FechaCierre(servicio.fijarFechaCierre(cuerpo == null ? null : cuerpo.fechaCierre(),
				Long.valueOf(jwt.getSubject())));
	}

}
