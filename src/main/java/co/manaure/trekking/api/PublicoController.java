package co.manaure.trekking.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.api.dto.ConfigResponse;
import co.manaure.trekking.api.dto.DisponibilidadResponse;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.reserva.DisponibilidadService;

/** Rutas públicas de lectura (diseño §6.2). */
@RestController
@RequestMapping("/api/v1")
public class PublicoController {

	private final ConfiguracionService configuracion;
	private final DisponibilidadService disponibilidad;

	public PublicoController(ConfiguracionService configuracion, DisponibilidadService disponibilidad) {
		this.configuracion = configuracion;
		this.disponibilidad = disponibilidad;
	}

	@GetMapping("/config")
	public ConfigResponse config() {
		return ConfigResponse.desde(configuracion.obtener());
	}

	@GetMapping("/disponibilidad")
	public DisponibilidadResponse disponibilidad() {
		DisponibilidadService.Resumen r = disponibilidad.consultar();
		return new DisponibilidadResponse(r.capacidad(), r.ocupados(), r.disponibles(), r.fechaCierre(), r.hoy());
	}

}
