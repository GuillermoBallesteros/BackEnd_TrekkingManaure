package co.manaure.trekking.regla;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import co.manaure.trekking.comun.error.CodigoError;

/** Actividades extra: existentes, sin repetir y {@code parapenteAcompanante} solo con acompañante. */
public final class ValidadorExtras {

	private ValidadorExtras() {
	}

	public static Optional<ErrorRegla> validar(List<String> extras, boolean hayAcompanante, CatalogoReglas catalogos) {
		List<String> lista = extras == null ? List.of() : extras;
		if (!lista.stream().allMatch(catalogos::esExtra)) {
			return ErrorRegla.de(CodigoError.EXTRA_INVALIDO, "Hay una actividad extra que no existe.", "extras");
		}
		if (new HashSet<>(lista).size() != lista.size()) {
			return ErrorRegla.de(CodigoError.EXTRA_INVALIDO, "Cada actividad extra se elige una sola vez.", "extras");
		}
		if (!hayAcompanante && lista.stream().anyMatch(catalogos::extraRequiereAcompanante)) {
			return ErrorRegla.de(CodigoError.EXTRA_INVALIDO,
					"El parapente del acompañante solo se elige si llevas acompañante.", "extras");
		}
		return Optional.empty();
	}

}
