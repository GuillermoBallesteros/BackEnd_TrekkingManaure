package co.manaure.trekking.regla;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.ValidadorTelefono.TipoTelefono;
import co.manaure.trekking.regla.modelo.ContactoRegla;
import co.manaure.trekking.regla.modelo.PersonaRegla;
import co.manaure.trekking.regla.modelo.SaludRegla;

/**
 * Datos de una persona, titular o acompañante (port de {@code validatePerson}). Devuelve el primer error en
 * el orden del diseño §8, paso 3. Los objetos anidados (teléfono, EPS, contacto, salud, compromisos,
 * consentimiento) se suponen presentes: su ausencia es un error de estructura (400) que se resuelve antes.
 */
public final class ValidadorPersona {

	private ValidadorPersona() {
	}

	public static Optional<ErrorRegla> validar(PersonaRegla p, Quien q, LocalDate hoy, CatalogoReglas catalogos,
			ParametrosEvento evento) {
		return ErrorRegla.primero(
				() -> nombres(p, q),
				() -> fechaNacimiento(p, q, hoy),
				() -> ValidadorDocumento.validar(p, q, hoy, catalogos),
				() -> genero(p, q, catalogos),
				() -> ValidadorTelefono.validar(p.telefono(), TipoTelefono.MOVIL, q.de(), catalogos,
						q.campo("phone")),
				() -> ValidadorCorreo.validar(p.correo(), q.de(), q.campo("email")),
				() -> ciudad(p, q),
				() -> eps(p, q, catalogos),
				() -> contactoEmergencia(p, q, catalogos),
				() -> salud(p.salud(), q),
				() -> compromisos(p, q),
				() -> consentimiento(p, q, evento),
				() -> estadia(p, q, catalogos));
	}

	public static Optional<ErrorRegla> nombres(PersonaRegla p, Quien q) {
		boolean nombresOk = Textos.nombreValido(p.nombres());
		if (nombresOk && Textos.nombreValido(p.apellidos())) {
			return Optional.empty();
		}
		return ErrorRegla.de(CodigoError.NOMBRE_INVALIDO,
				"Escribe nombres y apellidos " + q.de() + " solo con letras (2 a 60 caracteres).",
				q.campo(nombresOk ? "lastName" : "firstName"));
	}

	public static Optional<ErrorRegla> fechaNacimiento(PersonaRegla p, Quien q, LocalDate hoy) {
		Optional<LocalDate> nacimiento = Fechas.parsear(p.fechaNacimiento());
		if (nacimiento.isEmpty() || nacimiento.get().isAfter(hoy)
				|| Fechas.edad(nacimiento.get(), hoy) > Fechas.EDAD_MAXIMA) {
			return ErrorRegla.de(CodigoError.FECHA_NACIMIENTO_INVALIDA,
					"Revisa la fecha de nacimiento " + q.de() + ".", q.campo("birthDate"));
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> genero(PersonaRegla p, Quien q, CatalogoReglas catalogos) {
		return catalogos.esGenero(p.genero()) ? Optional.empty()
				: ErrorRegla.de(CodigoError.GENERO_INVALIDO, "Elige el género " + q.de() + ".", q.campo("gender"));
	}

	public static Optional<ErrorRegla> ciudad(PersonaRegla p, Quien q) {
		String ciudad = Textos.nz(p.ciudad());
		if (ciudad.length() < 3 || ciudad.length() > 60 || !Textos.lugarValido(ciudad)) {
			return ErrorRegla.de(CodigoError.CIUDAD_INVALIDA, "Escribe la ciudad " + q.de() + " solo con letras.",
					q.campo("city"));
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> eps(PersonaRegla p, Quien q, CatalogoReglas catalogos) {
		String entidad = p.eps().entidad();
		if (!catalogos.esEps(entidad)) {
			return ErrorRegla.de(CodigoError.EPS_INVALIDA, "Elige la EPS " + q.de() + ".", q.campo("eps.entity"));
		}
		String otra = p.eps().otra();
		if (catalogos.epsPideNombre(entidad)) {
			String nombre = Textos.nz(otra);
			if (nombre.length() < 3 || nombre.length() > 60 || !Textos.lugarValido(nombre)) {
				return ErrorRegla.de(CodigoError.EPS_INVALIDA, "Escribe el nombre de la EPS " + q.de() + ".",
						q.campo("eps.other"));
			}
		} else if (otra != null) {
			return ErrorRegla.de(CodigoError.EPS_INVALIDA, "Revisa la EPS " + q.de() + ".", q.campo("eps.other"));
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> contactoEmergencia(PersonaRegla p, Quien q, CatalogoReglas catalogos) {
		ContactoRegla contacto = p.contacto();
		String nombre = Textos.nz(contacto.nombre());
		if (!Textos.nombreValido(nombre, 5, 120) || nombre.split(" ").length < 2) {
			return ErrorRegla.de(CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
					"Escribe nombre y apellido del contacto de emergencia " + q.de() + ".",
					q.campo("emergencyContact.name"));
		}
		if (Textos.nombreCompletoComparable(p.nombres(), p.apellidos()).equals(Textos.nombreComparable(nombre))) {
			return ErrorRegla.de(CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
					"El contacto de emergencia " + q.de() + " debe ser otra persona.",
					q.campo("emergencyContact.name"));
		}
		Optional<ErrorRegla> telefono = ValidadorTelefono.validar(contacto.telefono(), TipoTelefono.CUALQUIERA,
				"del contacto de emergencia " + q.de(), catalogos, q.campo("emergencyContact.phone"));
		if (telefono.isPresent()) {
			return telefono;
		}
		if (ValidadorTelefono.phoneKey(contacto.telefono(), catalogos)
				.equals(ValidadorTelefono.phoneKey(p.telefono(), catalogos))) {
			return ErrorRegla.de(CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
					"El teléfono del contacto de emergencia " + q.de() + " debe ser distinto al suyo propio.",
					q.campo("emergencyContact.phone.number"));
		}
		if (!catalogos.esParentesco(contacto.parentesco())) {
			return ErrorRegla.de(CodigoError.CONTACTO_EMERGENCIA_INVALIDO,
					"Elige el parentesco del contacto de emergencia " + q.de() + ".",
					q.campo("emergencyContact.relationship"));
		}
		return Optional.empty();
	}

	/** Detalle opcional: nulo, o de 2 a 200 caracteres sin espacios en los extremos. */
	private static boolean detalleValido(String valor) {
		return valor == null || (valor.length() >= 2 && valor.length() <= 200 && valor.strip().equals(valor));
	}

	public static Optional<ErrorRegla> salud(SaludRegla salud, Quien q) {
		if (!detalleValido(salud.enfermedad())) {
			return ErrorRegla.de(CodigoError.SALUD_INVALIDA, "Describe la enfermedad " + q.de() + ".",
					q.campo("health.illness"));
		}
		if (!detalleValido(salud.alergias())) {
			return ErrorRegla.de(CodigoError.SALUD_INVALIDA, "Describe la alergia " + q.de() + ".",
					q.campo("health.allergies"));
		}
		if (!detalleValido(salud.discapacidad())) {
			return ErrorRegla.de(CodigoError.SALUD_INVALIDA, "Describe la discapacidad " + q.de() + ".",
					q.campo("health.disability"));
		}
		if (salud.ejercita() == null) {
			return ErrorRegla.de(CodigoError.SALUD_INVALIDA,
					"Indica si " + q.sujeto().toLowerCase(java.util.Locale.ROOT) + " practica deporte.",
					q.campo("health.exercises"));
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> compromisos(PersonaRegla p, Quien q) {
		if (!Boolean.TRUE.equals(p.compromisos().areasProtegidas())) {
			return ErrorRegla.de(CodigoError.COMPROMISO_REQUERIDO,
					q.sujeto() + " debe comprometerse a respetar las indicaciones y normas de las áreas protegidas.",
					q.campo("commitments.protectedAreas"));
		}
		if (!Boolean.TRUE.equals(p.compromisos().medioAmbiente())) {
			return ErrorRegla.de(CodigoError.COMPROMISO_REQUERIDO,
					q.sujeto() + " debe comprometerse a contribuir con el cuidado del medio ambiente.",
					q.campo("commitments.environment"));
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> consentimiento(PersonaRegla p, Quien q, ParametrosEvento evento) {
		var consentimiento = p.consentimiento();
		if (!fechaHoraValida(consentimiento.aceptadoEn())) {
			return ErrorRegla.de(CodigoError.CONSENTIMIENTO_REQUERIDO,
					"Falta la aceptación de los términos y condiciones " + q.de() + ".",
					q.campo("consent.acceptedAt"));
		}
		if (!evento.versionTerminos().equals(consentimiento.version())) {
			return ErrorRegla.de(CodigoError.TERMINOS_DESACTUALIZADOS,
					"Los términos cambiaron: vuelve a leerlos y acéptalos " + q.de() + ".",
					q.campo("consent.version"));
		}
		if (consentimiento.usoImagen() == null) {
			return ErrorRegla.de(CodigoError.USO_IMAGEN_INVALIDO,
					"Revisa la autorización de uso de imagen " + q.de() + ".", q.campo("consent.imageUse"));
		}
		return Optional.empty();
	}

	public static Optional<ErrorRegla> estadia(PersonaRegla p, Quien q, CatalogoReglas catalogos) {
		return catalogos.esEstadia(p.estadia()) ? Optional.empty()
				: ErrorRegla.de(CodigoError.ESTADIA_INVALIDA, "Elige la estadía " + q.de() + ".", q.campo("stay"));
	}

	/** Fecha-hora ISO-8601 (con zona, sin zona o solo fecha), como acepta {@code Date.parse} en los casos reales. */
	static boolean fechaHoraValida(String valor) {
		if (valor == null || valor.isBlank() || valor.length() > 64) {
			return false;
		}
		try {
			Instant.parse(valor);
			return true;
		} catch (DateTimeException e) {
			// siguiente formato
		}
		try {
			OffsetDateTime.parse(valor);
			return true;
		} catch (DateTimeException e) {
			// siguiente formato
		}
		try {
			LocalDateTime.parse(valor);
			return true;
		} catch (DateTimeException e) {
			// siguiente formato
		}
		return Fechas.esFechaIso(valor);
	}

}
