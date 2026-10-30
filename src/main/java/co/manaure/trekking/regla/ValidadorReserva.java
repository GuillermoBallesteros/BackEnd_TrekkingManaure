package co.manaure.trekking.regla;

import java.util.List;
import java.util.Optional;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.modelo.NuevaReserva;

/**
 * Validación completa de una reserva nueva (port de {@code validateNewRegistration}). Devuelve el PRIMER
 * error en el orden del diseño §8, pasos 2 a 11 (el paso 1, estructura, es previo y da 400).
 */
public final class ValidadorReserva {

	private ValidadorReserva() {
	}

	public static Optional<ErrorRegla> validar(NuevaReserva reserva, ContextoReserva ctx) {
		var catalogos = ctx.catalogos();
		var evento = ctx.evento();
		boolean hayAcompanante = reserva.acompanante() != null;
		return ErrorRegla.primero(
				() -> cierre(ctx),
				() -> ValidadorPersona.validar(reserva.titular(), Quien.TITULAR, ctx.hoy(), catalogos, evento),
				() -> menorConResponsable(reserva, ctx),
				() -> tallaTitular(reserva, ctx),
				() -> ValidadorExtras.validar(reserva.extras(), hayAcompanante, catalogos),
				() -> reserva.hotel() == null ? Optional.empty()
						: ValidadorHotel.validar(reserva.hotel(), hayAcompanante, ctx.hoy(), evento),
				() -> acompanante(reserva, ctx),
				() -> ValidadorPlan.validar(reserva.plan(), reserva.fechaCompromiso(), ctx.hoy(), evento),
				() -> documentosDuplicados(reserva, ctx),
				() -> cupoCarrera(ctx),
				() -> cupoParamo(reserva, ctx));
	}

	/** Cada reserva gasta un cupo de la carrera, con o sin acompañante. */
	public static Optional<ErrorRegla> cupoCarrera(ContextoReserva ctx) {
		return ctx.cuposCarreraDisponibles() > 0 ? Optional.empty()
				: ErrorRegla.de(CodigoError.CARRERA_AGOTADA,
						"Ya no hay cupos disponibles para la carrera. Será en una siguiente oportunidad.", null);
	}

	/** Las inscripciones y los pagos del restante se cierran después de la fecha de cierre. */
	public static Optional<ErrorRegla> cierre(ContextoReserva ctx) {
		return ctx.hoy().isAfter(ctx.evento().fechaCierre())
				? ErrorRegla.de(CodigoError.INSCRIPCIONES_CERRADAS,
						"Las inscripciones cerraron el " + Fechas.formatear(ctx.evento().fechaCierre()) + ".", null)
				: Optional.empty();
	}

	/** Un titular menor de edad exige un contacto que sea su madre, padre o tutor legal. */
	public static Optional<ErrorRegla> menorConResponsable(NuevaReserva reserva, ContextoReserva ctx) {
		var titular = reserva.titular();
		if (Fechas.esMenor(titular.fechaNacimiento(), ctx.hoy())
				&& !ctx.catalogos().esResponsableLegal(titular.contacto().parentesco())) {
			return ErrorRegla.de(CodigoError.MENOR_SIN_RESPONSABLE,
					"El participante es menor de edad: su contacto debe ser su madre, padre o tutor legal.",
					"emergencyContact.relationship");
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> tallaTitular(NuevaReserva reserva, ContextoReserva ctx) {
		return ctx.catalogos().esTalla(reserva.tallaTitular()) ? Optional.empty()
				: ErrorRegla.de(CodigoError.TALLA_INVALIDA, "Elige tu talla de camisa.", "shirtSize");
	}

	/** Acompañante (si viene): sus datos y talla, y luego que sea distinto del titular. */
	public static Optional<ErrorRegla> acompanante(NuevaReserva reserva, ContextoReserva ctx) {
		if (reserva.acompanante() == null) {
			return Optional.empty();
		}
		return ErrorRegla.primero(
				() -> ValidadorAcompanante.validar(reserva.acompanante(), ctx.hoy(), ctx.catalogos(), ctx.evento()),
				() -> ValidadorAcompanante.distinto(reserva.titular(), reserva.acompanante().persona(),
						ctx.catalogos()));
	}

	/** Documento del titular y luego del acompañante frente a las inscripciones activas (RN-06). */
	public static Optional<ErrorRegla> documentosDuplicados(NuevaReserva reserva, ContextoReserva ctx) {
		String titular = reserva.titular().documento();
		if (documentoTomado(titular, ctx)) {
			return duplicado(titular, "document");
		}
		if (reserva.acompanante() != null) {
			String acompanante = reserva.acompanante().persona().documento();
			if (documentoTomado(acompanante, ctx)) {
				return duplicado(acompanante, "companion.document");
			}
		}
		return Optional.empty();
	}

	private static boolean documentoTomado(String documento, ContextoReserva ctx) {
		return documento != null && !documento.isEmpty()
				&& ctx.documentosActivos().contains(Textos.normalizarDocumento(documento));
	}

	private static Optional<ErrorRegla> duplicado(String documento, String campo) {
		return ErrorRegla.de(CodigoError.DOCUMENTO_DUPLICADO,
				"El documento " + documento + " ya tiene una reserva registrada." + Textos.AYUDA_DOCUMENTO_AJENO, campo);
	}

	/**
	 * Cupos del páramo que necesita la reserva frente a los libres. Hotel en Manaure y regreso a casa no gastan
	 * cupo, así que solo falla si alguien elige el páramo y no cabe.
	 */
	public static Optional<ErrorRegla> cupoParamo(NuevaReserva reserva, ContextoReserva ctx) {
		List<String> estadias = reserva.acompanante() == null
				? List.of(reserva.titular().estadia())
				: List.of(reserva.titular().estadia(), reserva.acompanante().persona().estadia());
		int personas = CalculadoraCupos.personasParamo(estadias, ctx.catalogos());
		int libres = ctx.cuposDisponibles();
		if (personas == 0 || libres >= personas) {
			return Optional.empty();
		}
		String detalle = libres == 0
				? "Los cupos del páramo están agotados: elige hotel en Manaure o regreso a casa."
				: "Solo queda " + libres + " cupo en el páramo y tu reserva necesita " + personas
						+ ". Elige otra estadía para alguno de los dos.";
		return ErrorRegla.de(CodigoError.SIN_CUPO_PARAMO, detalle, "stay");
	}

}
