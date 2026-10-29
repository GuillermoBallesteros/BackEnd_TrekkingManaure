package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.CatalogoReglas.ReglaDocumento;
import co.manaure.trekking.regla.modelo.PersonaRegla;

/** Documento según su tipo: patrón y edades admitidas (port de {@code validateDocument}). */
public final class ValidadorDocumento {

	/** Ningún documento válido es más largo; se comprueba antes de aplicar la expresión regular. */
	static final int MAX_DOCUMENTO = 40;

	private ValidadorDocumento() {
	}

	/** El documento llega ya normalizado (sin puntos, espacios ni guiones y en mayúsculas), como en el front. */
	public static Optional<ErrorRegla> validar(PersonaRegla persona, Quien quien, LocalDate hoy,
			CatalogoReglas catalogos) {
		Optional<ReglaDocumento> regla = catalogos.documento(persona.tipoDocumento());
		if (regla.isEmpty()) {
			return ErrorRegla.de(CodigoError.TIPO_DOCUMENTO_INVALIDO,
					"Elige un tipo de documento válido " + quien.de() + ".", quien.campo("documentType"));
		}
		ReglaDocumento r = regla.get();
		String documento = Textos.nz(persona.documento());
		if (documento.length() > MAX_DOCUMENTO
				|| !Pattern.compile("(?:" + r.patron() + ")").matcher(documento).matches()) {
			return ErrorRegla.de(CodigoError.DOCUMENTO_INVALIDO, "El documento " + quien.de() + " no es válido: "
					+ r.ayuda().toLowerCase(Locale.ROOT) + ".", quien.campo("document"));
		}

		Optional<LocalDate> nacimiento = Fechas.parsear(persona.fechaNacimiento());
		if (nacimiento.isEmpty()) {
			return Optional.empty();
		}
		int edad = Fechas.edad(nacimiento.get(), hoy);
		if (r.edadMin() != null && edad < r.edadMin()) {
			return ErrorRegla.de(CodigoError.DOCUMENTO_EDAD_INCOMPATIBLE,
					"La " + persona.tipoDocumento() + " es para personas de " + r.edadMin()
							+ " años o más: revisa el tipo de documento o la fecha de nacimiento " + quien.de() + ".",
					quien.campo("documentType"));
		}
		if (r.edadMax() != null && edad > r.edadMax()) {
			return ErrorRegla.de(CodigoError.DOCUMENTO_EDAD_INCOMPATIBLE,
					"El " + persona.tipoDocumento() + " es para personas de hasta " + r.edadMax()
							+ " años: revisa el tipo de documento o la fecha de nacimiento " + quien.de() + ".",
					quien.campo("documentType"));
		}
		return Optional.empty();
	}

}
