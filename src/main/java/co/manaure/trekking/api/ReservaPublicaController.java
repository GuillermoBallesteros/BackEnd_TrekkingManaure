package co.manaure.trekking.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.api.dto.NewRegistration;
import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.reserva.ReservaService;
import jakarta.validation.Valid;

/** Registro público de reservas (diseño §6.2). Sin cabecera Location: no hay consulta pública. */
@RestController
@RequestMapping("/api/v1/reservas")
public class ReservaPublicaController {

	private final ReservaService servicio;

	public ReservaPublicaController(ReservaService servicio) {
		this.servicio = servicio;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Registration crear(@Valid @RequestBody NewRegistration entrada) {
		return servicio.crear(entrada);
	}

}
