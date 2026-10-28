package co.manaure.trekking.pago;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.regla.CalculadoraCupos;
import co.manaure.trekking.regla.CatalogoReglas;
import co.manaure.trekking.regla.Fechas;
import co.manaure.trekking.regla.adaptador.CatalogoReglasAdaptador;
import co.manaure.trekking.regla.modelo.PagoRegla;
import co.manaure.trekking.reserva.Inscripcion;
import co.manaure.trekking.reserva.InscripcionParticipante;
import co.manaure.trekking.reserva.InscripcionParticipanteRepository;
import co.manaure.trekking.reserva.RolParticipante;

/**
 * Reglas comunes de la aprobación de un pago (RN-05, RN-08, RN-15, RN-18): las usan la revisión de pagos (HU-N08)
 * y el pago manual (HU-N09). Se invoca con los bloqueos ya tomados (configuración y luego inscripción).
 */
@Component
public class AprobacionPago {

	private final InscripcionParticipanteRepository participantes;
	private final CatalogoReglasAdaptador catalogos;

	public AprobacionPago(InscripcionParticipanteRepository participantes, CatalogoReglasAdaptador catalogos) {
		this.participantes = participantes;
		this.catalogos = catalogos;
	}

	/**
	 * Valida, en este orden: menor (solo si se aprueba), documento (si la reserva pasa de inactiva a activa) y cupo
	 * (solo si se aprueba y la reserva aún no retiene cupo). Lanza {@link NegocioException} sin tocar nada.
	 */
	public void validar(ConfiguracionEvento cfg, Inscripcion inscripcion, List<InscripcionParticipante> personas,
			List<Pago> pagosActuales, boolean aprobando, boolean activaDespues, LocalDate hoy) {
		if (aprobando) {
			validarMenor(inscripcion, personas, hoy);
		}
		if (activaDespues && !inscripcion.isActiva()) {
			validarDocumentos(inscripcion, personas);
		}
		if (aprobando && !retieneCupo(pagosActuales)) {
			validarCupos(cfg, inscripcion, personas);
		}
	}

	/** Mantiene coherentes {@code activa} y {@code documento_activo} con el estado derivado. */
	public void fijarActiva(Inscripcion inscripcion, List<InscripcionParticipante> personas, boolean activa) {
		inscripcion.setActiva(activa);
		for (InscripcionParticipante p : personas) {
			p.setDocumentoActivo(activa ? p.getPersona().getDocumento() : null);
		}
	}

	private static boolean retieneCupo(List<Pago> todos) {
		return CalculadoraCupos
				.retieneCupo(todos.stream().map(p -> new PagoRegla(p.getMonto(), p.getRevision())).toList());
	}

	private void validarMenor(Inscripcion inscripcion, List<InscripcionParticipante> personas, LocalDate hoy) {
		InscripcionParticipante titular = personas.stream().filter(p -> p.getRol() == RolParticipante.TITULAR)
				.findFirst().orElseThrow();
		boolean menor = Fechas.edad(titular.getPersona().getFechaNacimiento(), hoy) < Fechas.MAYORIA_EDAD;
		if (menor && !inscripcion.isAutorizacionMenor()) {
			throw new NegocioException(CodigoError.AUTORIZACION_MENOR_PENDIENTE, HttpStatus.UNPROCESSABLE_ENTITY,
					"El titular es menor de edad: falta la autorización firmada por su responsable.");
		}
	}

	/** RN-18: reactivar una reserva Rechazada falla si otra inscripción activa ya tomó alguno de sus documentos. */
	private void validarDocumentos(Inscripcion inscripcion, List<InscripcionParticipante> personas) {
		Set<String> documentos = personas.stream().map(p -> p.getPersona().getDocumento())
				.collect(Collectors.toSet());
		List<String> tomados = participantes.documentosTomadosPorOtras(documentos, inscripcion.getId());
		if (!tomados.isEmpty()) {
			// Primero el titular, luego el acompañante, como al registrar.
			String documento = personas.stream().sorted(Comparator.comparing(InscripcionParticipante::getRol))
					.map(p -> p.getPersona().getDocumento()).filter(tomados::contains).findFirst()
					.orElse(tomados.get(0));
			throw new NegocioException(CodigoError.DOCUMENTO_DUPLICADO, HttpStatus.CONFLICT,
					"El documento " + documento + " ya tiene una reserva registrada.");
		}
	}

	/** RN-05: al aprobar el primer pago, las personas en Páramo deben caber en los cupos libres (sin esta reserva). */
	private void validarCupos(ConfiguracionEvento cfg, Inscripcion inscripcion,
			List<InscripcionParticipante> personas) {
		CatalogoReglas catalogo = catalogos.catalogos();
		int enParamo = CalculadoraCupos.personasParamo(
				personas.stream().map(InscripcionParticipante::getEstadia).toList(), catalogo);
		long libres = Math.max(0, cfg.getCuposParamo() - participantes.contarOcupados(inscripcion.getId()));
		if (libres < enParamo) {
			throw new NegocioException(CodigoError.SIN_CUPO_PARA_APROBAR, HttpStatus.CONFLICT,
					"No hay cupos en el páramo para confirmar esta inscripción (" + enParamo + " en páramo, "
							+ libres + " libres). Cambia los cupos del páramo en Gestión de cupos.");
		}
	}

}
