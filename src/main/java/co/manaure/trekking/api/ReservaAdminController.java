package co.manaure.trekking.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.api.dto.AdminReservas;
import co.manaure.trekking.api.dto.AutorizacionMenorRequest;
import co.manaure.trekking.api.dto.PagoManualRequest;
import co.manaure.trekking.api.dto.PlazoRequest;
import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.api.dto.TiposReserva.Payment;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.pago.PagoManualService;
import co.manaure.trekking.reserva.ReservaAdminService;
import co.manaure.trekking.reserva.VencimientoService;

/** Administración de reservas (diseño §6.4). La seguridad de {@code /api/v1/admin/**} la aplica SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/reservas")
public class ReservaAdminController {

	private final ReservaAdminService servicio;
	private final PagoManualService pagoManual;
	private final VencimientoService vencimiento;

	public ReservaAdminController(ReservaAdminService servicio, PagoManualService pagoManual,
			VencimientoService vencimiento) {
		this.servicio = servicio;
		this.pagoManual = pagoManual;
		this.vencimiento = vencimiento;
	}

	@GetMapping
	public AdminReservas listar() {
		return servicio.listar();
	}

	/** HU-N09. */
	@PostMapping("/{id}/pagos")
	@ResponseStatus(HttpStatus.CREATED)
	public Payment registrarPago(@PathVariable("id") String id, @RequestBody PagoManualRequest cuerpo,
			@AuthenticationPrincipal Jwt jwt) {
		return ReservaMapper.pago(pagoManual.registrar(id, cuerpo.montoEntero(), Long.valueOf(jwt.getSubject())));
	}

	/** HU-N10. */
	@PutMapping("/{id}/autorizacion-menor")
	public Registration fijarAutorizacionMenor(@PathVariable("id") String id,
			@RequestBody AutorizacionMenorRequest cuerpo, @AuthenticationPrincipal Jwt jwt) {
		if (!cuerpo.esBooleano()) {
			throw new NegocioException(CodigoError.DATOS_INVALIDOS, HttpStatus.BAD_REQUEST,
					"Los datos enviados están incompletos o tienen un formato inválido.");
		}
		return servicio.fijarAutorizacionMenor(id, cuerpo.received().booleanValue(), Long.valueOf(jwt.getSubject()));
	}

	/** RN-25: amplía de 1 a 5 días el plazo de una reserva vencida. */
	@PutMapping("/{id}/plazo")
	public Registration ampliarPlazo(@PathVariable("id") String id, @RequestBody PlazoRequest cuerpo,
			@AuthenticationPrincipal Jwt jwt) {
		return vencimiento.ampliarPlazo(id, cuerpo.diasEnteros(), Long.valueOf(jwt.getSubject()));
	}

	/** RN-26: rechaza una reserva vencida y libera sus cupos. */
	@PostMapping("/{id}/rechazo")
	public Registration rechazar(@PathVariable("id") String id, @AuthenticationPrincipal Jwt jwt) {
		return vencimiento.rechazar(id, Long.valueOf(jwt.getSubject()));
	}

}
