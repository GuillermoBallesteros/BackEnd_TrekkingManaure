package co.manaure.trekking.regla.adaptador;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Component;

import co.manaure.trekking.catalogo.CatalogoService;
import co.manaure.trekking.catalogo.PaisTelefono;
import co.manaure.trekking.catalogo.TipoDocumento;
import co.manaure.trekking.regla.CatalogoReglas;
import co.manaure.trekking.regla.CatalogoReglas.ReglaDocumento;
import co.manaure.trekking.regla.CatalogoReglas.ReglaNumero;
import co.manaure.trekking.regla.CatalogoReglas.ReglaPais;

/**
 * Adaptador fino: arma el {@link CatalogoReglas} (datos puros) desde {@link CatalogoService}, que cachea los
 * catálogos. Las reglas no dependen de esta clase.
 */
@Component
public class CatalogoReglasAdaptador {

	private final CatalogoService catalogo;

	public CatalogoReglasAdaptador(CatalogoService catalogo) {
		this.catalogo = catalogo;
	}

	public CatalogoReglas catalogos() {
		return new CatalogoReglas(
				mapa(catalogo.tiposDocumento(), TipoDocumento::getClave, t -> regla(t)),
				catalogo.generos().stream().map(g -> g.getClave()).toList(),
				catalogo.tallas().stream().map(t -> t.getClave()).toList(),
				mapa(catalogo.estadias(), e -> e.getClave(), e -> e.isOcupaCupo()),
				mapa(catalogo.extras(), e -> e.getClave(), e -> e.isRequiereAcompanante()),
				mapa(catalogo.eps(), e -> e.getClave(), e -> e.isPideNombre()),
				mapa(catalogo.parentescos(), p -> p.getClave(), p -> p.isEsResponsableLegal()),
				mapa(catalogo.paises(), PaisTelefono::getIso, p -> regla(p)));
	}

	private ReglaDocumento regla(TipoDocumento t) {
		return new ReglaDocumento(t.getPatron(), t.getAyuda(), entero(t.getEdadMin()), entero(t.getEdadMax()));
	}

	private ReglaPais regla(PaisTelefono p) {
		ReglaNumero fijo = p.admiteFijo()
				? new ReglaNumero(enteros(p.getFijoLargos()), Arrays.asList(p.getFijoInicios()))
				: null;
		return new ReglaPais(p.getIso(), p.getNombre(), p.getIndicativo(), p.getEjemplo(),
				new ReglaNumero(enteros(p.getMovilLargos()), Arrays.asList(p.getMovilInicios())), fijo);
	}

	private static Integer entero(Short valor) {
		return valor == null ? null : valor.intValue();
	}

	private static List<Integer> enteros(Short[] valores) {
		return Arrays.stream(valores).map(Short::intValue).toList();
	}

	private static <T, V> Map<String, V> mapa(List<T> lista, Function<T, String> clave, Function<T, V> valor) {
		Map<String, V> mapa = new LinkedHashMap<>();
		lista.forEach(e -> mapa.put(clave.apply(e), valor.apply(e)));
		return mapa;
	}

}
