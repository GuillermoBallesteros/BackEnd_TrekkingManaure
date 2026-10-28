package co.manaure.trekking.regla;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Catálogos que necesitan las reglas, como datos inmutables (sin Spring ni base). Las claves son el mismo
 * texto que usa el front. El orden de los mapas se conserva (importa para detectar el país por indicativo).
 *
 * @param documentos  tipo de documento a su regla
 * @param generos     claves de género
 * @param tallas      claves de talla
 * @param estadias    estadía a {@code ocupa_cupo}
 * @param extras      extra a {@code requiere_acompanante}
 * @param eps         EPS a {@code pide_nombre} ("Otra")
 * @param parentescos parentesco a {@code es_responsable_legal}
 * @param paises      iso a su regla de teléfono
 */
public record CatalogoReglas(Map<String, ReglaDocumento> documentos, List<String> generos, List<String> tallas,
		Map<String, Boolean> estadias, Map<String, Boolean> extras, Map<String, Boolean> eps,
		Map<String, Boolean> parentescos, Map<String, ReglaPais> paises) {

	/** Formato de un tipo de documento: patrón sin anclas, ayuda y edades admitidas (nulas = sin límite). */
	public record ReglaDocumento(String patron, String ayuda, Integer edadMin, Integer edadMax) {
	}

	/** Largos (dígitos sin indicativo) y prefijos válidos de un tipo de número. */
	public record ReglaNumero(List<Integer> largos, List<String> inicios) {

		public ReglaNumero {
			largos = List.copyOf(largos);
			inicios = List.copyOf(inicios);
		}

		public boolean cumple(String numero) {
			return largos.contains(numero.length()) && inicios.stream().anyMatch(numero::startsWith);
		}
	}

	/** País con indicativo y reglas de celular y fijo ({@code fijo} nulo si no admite fijo). */
	public record ReglaPais(String iso, String nombre, String indicativo, String ejemplo, ReglaNumero movil,
			ReglaNumero fijo) {
	}

	public CatalogoReglas {
		documentos = ordenado(documentos);
		generos = List.copyOf(generos);
		tallas = List.copyOf(tallas);
		estadias = ordenado(estadias);
		extras = ordenado(extras);
		eps = ordenado(eps);
		parentescos = ordenado(parentescos);
		paises = ordenado(paises);
	}

	private static <V> Map<String, V> ordenado(Map<String, V> origen) {
		return Collections.unmodifiableMap(new LinkedHashMap<>(origen));
	}

	public Optional<ReglaDocumento> documento(String tipo) {
		return Optional.ofNullable(tipo == null ? null : documentos.get(tipo));
	}

	public Optional<ReglaPais> pais(String iso) {
		return Optional.ofNullable(iso == null ? null : paises.get(iso));
	}

	public boolean esGenero(String valor) {
		return valor != null && generos.contains(valor);
	}

	public boolean esTalla(String valor) {
		return valor != null && tallas.contains(valor);
	}

	public boolean esEstadia(String valor) {
		return valor != null && estadias.containsKey(valor);
	}

	/** La estadía gasta cupo del páramo. */
	public boolean ocupaCupo(String estadia) {
		return estadia != null && Boolean.TRUE.equals(estadias.get(estadia));
	}

	public boolean esExtra(String valor) {
		return valor != null && extras.containsKey(valor);
	}

	public boolean extraRequiereAcompanante(String extra) {
		return Boolean.TRUE.equals(extras.get(extra));
	}

	public boolean esEps(String valor) {
		return valor != null && eps.containsKey(valor);
	}

	/** La EPS pide escribir el nombre ("Otra"). */
	public boolean epsPideNombre(String valor) {
		return valor != null && Boolean.TRUE.equals(eps.get(valor));
	}

	public boolean esParentesco(String valor) {
		return valor != null && parentescos.containsKey(valor);
	}

	public boolean esResponsableLegal(String parentesco) {
		return parentesco != null && Boolean.TRUE.equals(parentescos.get(parentesco));
	}

}
