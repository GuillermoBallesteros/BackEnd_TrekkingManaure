package co.manaure.trekking.regla;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.regla.CatalogoReglas.ReglaPais;
import co.manaure.trekking.regla.modelo.Telefono;

/** Teléfonos por país (port de {@code telefono.ts}). */
public final class ValidadorTelefono {

	/** 'movil' exige celular (WhatsApp); 'cualquiera' también acepta fijo. */
	public enum TipoTelefono { MOVIL, CUALQUIERA }

	/** Resultado de leer un indicativo escrito con + o 00. */
	public record Deteccion(Estado estado, String pais, String resto) {
		public enum Estado { DETECTADO, INCOMPLETO, DESCONOCIDO }
	}

	/** Ningún número telefónico real supera esta cantidad de dígitos. */
	static final int MAX_DIGITOS = 30;
	private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
	private static final Pattern NO_DIGITO = Pattern.compile("\\D");

	private ValidadorTelefono() {
	}

	public static String soloDigitos(String valor) {
		return NO_DIGITO.matcher(Textos.nz(valor)).replaceAll("");
	}

	/**
	 * Primer problema del teléfono. {@code quien} completa el mensaje ("del participante") y {@code campo} es la
	 * ruta del teléfono ({@code phone}, {@code companion.phone}...): el error apunta a {@code .country} o
	 * {@code .number}.
	 */
	public static Optional<ErrorRegla> validar(Telefono telefono, TipoTelefono tipo, String quien,
			CatalogoReglas catalogos, String campo) {
		Optional<ReglaPais> pais = catalogos.pais(telefono == null ? null : telefono.pais());
		if (pais.isEmpty()) {
			return ErrorRegla.de(CodigoError.TELEFONO_INVALIDO, "Elige el país del teléfono " + quien + ".",
					campo + ".country");
		}
		String numero = Textos.nz(telefono.numero());
		// Longitud antes de aplicar expresiones regulares: evita trabajo con textos enormes.
		if (numero.length() > MAX_DIGITOS) {
			return ErrorRegla.de(CodigoError.TELEFONO_INVALIDO,
					"El teléfono " + quien + " no es válido para " + pais.get().nombre() + ".", campo + ".number");
		}
		if (!SOLO_DIGITOS.matcher(numero).matches()) {
			return ErrorRegla.de(CodigoError.TELEFONO_INVALIDO,
					"Escribe el teléfono " + quien + " solo con números.", campo + ".number");
		}
		ReglaPais p = pais.get();
		boolean valido = p.movil().cumple(numero)
				|| (tipo == TipoTelefono.CUALQUIERA && p.fijo() != null && p.fijo().cumple(numero));
		if (valido) {
			return Optional.empty();
		}
		return ErrorRegla.de(CodigoError.TELEFONO_INVALIDO,
				(tipo == TipoTelefono.MOVIL ? "El celular " : "El teléfono ") + quien + " no es válido para "
						+ p.nombre() + " (ejemplo: " + p.ejemplo() + ").",
				campo + ".number");
	}

	/** Número completo comparable: +573001234567. */
	public static String phoneKey(Telefono telefono, CatalogoReglas catalogos) {
		String indicativo = catalogos.pais(telefono.pais()).map(ReglaPais::indicativo).orElse("");
		return "+" + indicativo + Textos.nz(telefono.numero());
	}

	/** Número nacional en dígitos; si alguien escribió el indicativo delante, se le quita. */
	public static String normalizarNumero(String pais, String valor, CatalogoReglas catalogos) {
		String digitos = soloDigitos(valor);
		Optional<ReglaPais> regla = catalogos.pais(pais);
		if (regla.isEmpty()) {
			return digitos;
		}
		ReglaPais p = regla.get();
		List<Integer> largos = new java.util.ArrayList<>(p.movil().largos());
		if (p.fijo() != null) {
			largos.addAll(p.fijo().largos());
		}
		String sinIndicativo = digitos.length() >= p.indicativo().length()
				? digitos.substring(p.indicativo().length())
				: "";
		return !largos.contains(digitos.length()) && digitos.startsWith(p.indicativo())
				&& largos.contains(sinIndicativo.length()) ? sinIndicativo : digitos;
	}

	/**
	 * Lee un número que empieza con + o 00 y elige el país por su indicativo. El +1 lo comparten Estados Unidos y
	 * República Dominicana: se espera el código de área para decidir.
	 */
	public static Deteccion detectarPais(String valor, CatalogoReglas catalogos) {
		String recortado = Textos.nz(valor).strip();
		if (!recortado.startsWith("+") && !recortado.startsWith("00")) {
			return new Deteccion(Deteccion.Estado.DESCONOCIDO, null, null);
		}
		String digitos = soloDigitos(recortado);
		if (digitos.startsWith("00")) {
			digitos = digitos.substring(2);
		}
		final String d = digitos;
		List<ReglaPais> candidatos = catalogos.paises().values().stream()
				.filter(p -> d.startsWith(p.indicativo())).toList();

		if (candidatos.isEmpty()) {
			boolean prefijo = catalogos.paises().values().stream().anyMatch(p -> p.indicativo().startsWith(d));
			return new Deteccion(prefijo ? Deteccion.Estado.INCOMPLETO : Deteccion.Estado.DESCONOCIDO, null, null);
		}

		int mayor = candidatos.stream().mapToInt(p -> p.indicativo().length()).max().orElse(0);
		List<ReglaPais> mejores = candidatos.stream().filter(p -> p.indicativo().length() == mayor).toList();
		String resto = d.substring(mejores.get(0).indicativo().length());

		if (mejores.size() > 1) {
			if (resto.length() < 3) {
				return new Deteccion(Deteccion.Estado.INCOMPLETO, null, null);
			}
			// Gana el país con el prefijo más largo que coincide: 809 (República Dominicana) antes que 8.
			ReglaPais ganador = mejores.get(0);
			for (ReglaPais p : mejores) {
				if (coincidencia(p, resto) > coincidencia(ganador, resto)) {
					ganador = p;
				}
			}
			return new Deteccion(Deteccion.Estado.DETECTADO, ganador.iso(), resto);
		}
		return new Deteccion(Deteccion.Estado.DETECTADO, mejores.get(0).iso(), resto);
	}

	private static int coincidencia(ReglaPais pais, String resto) {
		return pais.movil().inicios().stream().filter(resto::startsWith).mapToInt(String::length).max().orElse(0);
	}

}
