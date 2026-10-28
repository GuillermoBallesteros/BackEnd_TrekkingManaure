package co.manaure.trekking.catalogo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

/**
 * Catálogos en memoria (solo lectura). Se cargan en el primer uso y se conservan hasta {@link #recargar()}:
 * cambian solo por migración, así que una caché simple basta.
 */
@Service
public class CatalogoService {

	private record Instantanea(
			Map<String, TipoDocumento> tiposDocumento,
			Map<String, Genero> generos,
			Map<String, Talla> tallas,
			Map<String, Estadia> estadias,
			Map<String, Extra> extras,
			Map<String, Eps> eps,
			Map<String, Parentesco> parentescos,
			Map<String, PaisTelefono> paises) {
	}

	private final TipoDocumentoRepository tipoDocumentoRepositorio;
	private final GeneroRepository generoRepositorio;
	private final TallaRepository tallaRepositorio;
	private final EstadiaRepository estadiaRepositorio;
	private final ExtraRepository extraRepositorio;
	private final EpsRepository epsRepositorio;
	private final ParentescoRepository parentescoRepositorio;
	private final PaisTelefonoRepository paisRepositorio;

	private volatile Instantanea cache;

	public CatalogoService(TipoDocumentoRepository tipoDocumentoRepositorio, GeneroRepository generoRepositorio,
			TallaRepository tallaRepositorio, EstadiaRepository estadiaRepositorio, ExtraRepository extraRepositorio,
			EpsRepository epsRepositorio, ParentescoRepository parentescoRepositorio,
			PaisTelefonoRepository paisRepositorio) {
		this.tipoDocumentoRepositorio = tipoDocumentoRepositorio;
		this.generoRepositorio = generoRepositorio;
		this.tallaRepositorio = tallaRepositorio;
		this.estadiaRepositorio = estadiaRepositorio;
		this.extraRepositorio = extraRepositorio;
		this.epsRepositorio = epsRepositorio;
		this.parentescoRepositorio = parentescoRepositorio;
		this.paisRepositorio = paisRepositorio;
	}

	/** Descarta la caché; la siguiente consulta vuelve a leer la base. */
	public void recargar() {
		cache = null;
	}

	public List<TipoDocumento> tiposDocumento() { return List.copyOf(datos().tiposDocumento().values()); }
	public List<Genero> generos() { return List.copyOf(datos().generos().values()); }
	public List<Talla> tallas() { return List.copyOf(datos().tallas().values()); }
	public List<Estadia> estadias() { return List.copyOf(datos().estadias().values()); }
	public List<Extra> extras() { return List.copyOf(datos().extras().values()); }
	public List<Eps> eps() { return List.copyOf(datos().eps().values()); }
	public List<Parentesco> parentescos() { return List.copyOf(datos().parentescos().values()); }
	public List<PaisTelefono> paises() { return List.copyOf(datos().paises().values()); }

	public Optional<TipoDocumento> tipoDocumento(String clave) { return Optional.ofNullable(datos().tiposDocumento().get(clave)); }
	public Optional<Genero> genero(String clave) { return Optional.ofNullable(datos().generos().get(clave)); }
	public Optional<Talla> talla(String clave) { return Optional.ofNullable(datos().tallas().get(clave)); }
	public Optional<Estadia> estadia(String clave) { return Optional.ofNullable(datos().estadias().get(clave)); }
	public Optional<Extra> extra(String clave) { return Optional.ofNullable(datos().extras().get(clave)); }
	public Optional<Eps> eps(String clave) { return Optional.ofNullable(datos().eps().get(clave)); }
	public Optional<Parentesco> parentesco(String clave) { return Optional.ofNullable(datos().parentescos().get(clave)); }
	public Optional<PaisTelefono> pais(String iso) { return Optional.ofNullable(datos().paises().get(iso)); }

	private Instantanea datos() {
		Instantanea actual = cache;
		if (actual == null) {
			synchronized (this) {
				actual = cache;
				if (actual == null) {
					actual = cargar();
					cache = actual;
				}
			}
		}
		return actual;
	}

	/** Las entidades son inmutables y se leen completas, por lo que no necesitan sesión abierta después. */
	Instantanea cargar() {
		return new Instantanea(
				indexar(tipoDocumentoRepositorio.findAllByOrderByOrdenAsc(), TipoDocumento::getClave),
				indexar(generoRepositorio.findAllByOrderByOrdenAsc(), Genero::getClave),
				indexar(tallaRepositorio.findAllByOrderByOrdenAsc(), Talla::getClave),
				indexar(estadiaRepositorio.findAllByOrderByOrdenAsc(), Estadia::getClave),
				indexar(extraRepositorio.findAllByOrderByOrdenAsc(), Extra::getClave),
				indexar(epsRepositorio.findAllByOrderByOrdenAsc(), Eps::getClave),
				indexar(parentescoRepositorio.findAllByOrderByOrdenAsc(), Parentesco::getClave),
				indexar(paisRepositorio.findAllByOrderByOrdenAsc(), PaisTelefono::getIso));
	}

	/** Conserva el orden del catálogo. */
	private static <T> Map<String, T> indexar(List<T> lista, Function<T, String> clave) {
		return lista.stream().collect(Collectors.toMap(clave, Function.identity(), (a, b) -> a,
				java.util.LinkedHashMap::new));
	}

}
