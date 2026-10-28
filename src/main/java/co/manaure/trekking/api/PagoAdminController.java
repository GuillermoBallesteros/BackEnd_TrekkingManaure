package co.manaure.trekking.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.api.dto.RevisionPagoRequest;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.pago.RevisionPagoService;

/** Revisión de pagos del panel (HU-N08, diseño §6.4). La seguridad de {@code /api/v1/admin/**} la aplica SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin/pagos")
public class PagoAdminController {

	private final RevisionPagoService servicio;

	public PagoAdminController(RevisionPagoService servicio) {
		this.servicio = servicio;
	}

	@PutMapping("/{id}/revision")
	public Registration revisar(@PathVariable("id") String id, @jakarta.validation.Valid @RequestBody RevisionPagoRequest cuerpo,
			@AuthenticationPrincipal Jwt jwt) {
		RevisionPago revision = revision(cuerpo.review());
		return servicio.revisar(id, revision, cuerpo.reason(), Long.valueOf(jwt.getSubject()));
	}

	private static RevisionPago revision(String texto) {
		if (texto != null) {
			switch (texto) {
				case "Aprobado":
					return RevisionPago.APROBADO;
				case "Rechazado":
					return RevisionPago.RECHAZADO;
				case "Por revisar":
					return RevisionPago.POR_REVISAR;
				default:
					break;
			}
		}
		throw new NegocioException(CodigoError.REVISION_INVALIDA, HttpStatus.BAD_REQUEST,
				"La revisión del pago no es válida.");
	}

}
