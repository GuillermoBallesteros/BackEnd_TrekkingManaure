package co.manaure.trekking.regla;

import java.util.Optional;
import java.util.function.Supplier;

import co.manaure.trekking.comun.error.CodigoError;

/**
 * Primer error de una regla: código estable, mensaje en español (idéntico al del front) y ruta del campo
 * (por ejemplo {@code companion.phone.number}; nula si no aplica a un campo).
 * {@code toString()} omite el mensaje porque puede contener datos personales (p. ej. el documento).
 */
public record ErrorRegla(CodigoError code, String detail, String campo) {

	public static Optional<ErrorRegla> de(CodigoError code, String detail, String campo) {
		return Optional.of(new ErrorRegla(code, detail, campo));
	}

	/** Evalúa en orden y devuelve el primer error; las siguientes reglas no se ejecutan. */
	@SafeVarargs
	public static Optional<ErrorRegla> primero(Supplier<Optional<ErrorRegla>>... reglas) {
		for (Supplier<Optional<ErrorRegla>> regla : reglas) {
			Optional<ErrorRegla> error = regla.get();
			if (error.isPresent()) {
				return error;
			}
		}
		return Optional.empty();
	}

	@Override
	public String toString() {
		return "ErrorRegla[code=" + code + ", campo=" + campo + "]";
	}

}
