package co.manaure.trekking.reserva;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.api.ReservaMapper;
import co.manaure.trekking.api.dto.AdminReservas;
import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.comun.error.CodigoError;
import co.manaure.trekking.comun.error.NegocioException;
import co.manaure.trekking.configuracion.ConfiguracionEvento;
import co.manaure.trekking.configuracion.ConfiguracionService;
import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.pago.PagoRepository;
import co.manaure.trekking.persona.ConsentimientoPersona;
import co.manaure.trekking.persona.ConsentimientoPersonaRepository;
import co.manaure.trekking.persona.ContactoEmergencia;
import co.manaure.trekking.persona.ContactoEmergenciaRepository;
import co.manaure.trekking.persona.FichaSalud;
import co.manaure.trekking.persona.FichaSaludRepository;
import co.manaure.trekking.reserva.ReservaCompleta.Integrante;

/**
 * Lista del panel (HU-N07, diseño §6.4): todas las reservas, activas o no, completas y con la salud descifrada.
 * Carga por lotes con IN (una sentencia por tabla): el número de sentencias no depende de la cantidad de reservas.
 */
@Service
public class ReservaAdminService {

	private final ConfiguracionService configuracion;
	private final InscripcionRepository inscripciones;
	private final InscripcionParticipanteRepository participantes;
	private final FichaSaludRepository fichas;
	private final ContactoEmergenciaRepository contactos;
	private final ConsentimientoPersonaRepository consentimientos;
	private final InscripcionExtraRepository extras;
	private final HotelPrevioRepository hoteles;
	private final PagoRepository pagos;
	private final ReservaMapper mapper;
	private final Clock clock;
	private final AuditoriaService auditoria;

	public ReservaAdminService(ConfiguracionService configuracion, InscripcionRepository inscripciones,
			InscripcionParticipanteRepository participantes, FichaSaludRepository fichas,
			ContactoEmergenciaRepository contactos, ConsentimientoPersonaRepository consentimientos,
			InscripcionExtraRepository extras, HotelPrevioRepository hoteles, PagoRepository pagos,
			ReservaMapper mapper, Clock clock, AuditoriaService auditoria) {
		this.auditoria = auditoria;
		this.configuracion = configuracion;
		this.inscripciones = inscripciones;
		this.participantes = participantes;
		this.fichas = fichas;
		this.contactos = contactos;
		this.consentimientos = consentimientos;
		this.extras = extras;
		this.hoteles = hoteles;
		this.pagos = pagos;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public AdminReservas listar() {
		ConfiguracionEvento cfg = configuracion.obtener();
		LocalDate hoy = LocalDate.now(clock);
		List<Inscripcion> lista = inscripciones.findAllByOrderByIdDesc();
		if (lista.isEmpty()) {
			return new AdminReservas(List.of(), cfg.getCuposParamo(), cfg.getFechaCierre(), hoy);
		}
		return new AdminReservas(registrations(lista), cfg.getCuposParamo(), cfg.getFechaCierre(), hoy);
	}

	/**
	 * HU-N10 (RN-08): fija si se recibió la autorización firmada del responsable del menor. Solo toma la fila de la
	 * inscripción (diseño §9); no cambia ningún pago. Se acepta también sobre un titular mayor [SUPUESTO §16 nº 12].
	 */
	@Transactional
	public Registration fijarAutorizacionMenor(String codigo, boolean recibida, Long administradorId) {
		Inscripcion inscripcion = inscripciones.findByCodigoForUpdate(codigo)
				.orElseThrow(() -> new NegocioException(CodigoError.RESERVA_NO_ENCONTRADA, HttpStatus.NOT_FOUND,
						"La inscripción ya no existe."));
		inscripcion.setAutorizacionMenor(recibida);
		auditoria.registrar(administradorId, AccionAuditoria.FIJAR_AUTORIZACION_MENOR, EntidadAuditoria.RESERVA,
				inscripcion.getCodigo(), "received=" + recibida);
		return registrations(List.of(inscripcion)).get(0);
	}

	/**
	 * Arma las {@code Registration} completas de las inscripciones dadas, por lotes (una sentencia por tabla).
	 * Lo reutilizan la lista del panel y la respuesta de la revisión de pagos (HU-N08).
	 */
	public List<Registration> registrations(List<Inscripcion> lista) {
		List<Long> ids = lista.stream().map(Inscripcion::getId).toList();

		List<InscripcionParticipante> todos = participantes.porInscripciones(ids);
		List<Long> personas = todos.stream().map(p -> p.getPersona().getId()).toList();
		Map<Long, FichaSalud> fichaPorPersona = porPersona(fichas.porPersonas(personas), FichaSalud::getPersonaId);
		Map<Long, ContactoEmergencia> contactoPorPersona = porPersona(contactos.porPersonas(personas),
				ContactoEmergencia::getPersonaId);
		Map<Long, ConsentimientoPersona> consentimientoPorPersona = porPersona(consentimientos.porPersonas(personas),
				ConsentimientoPersona::getPersonaId);

		Map<Long, List<InscripcionParticipante>> participantesPorInscripcion = todos.stream()
				.collect(Collectors.groupingBy(p -> p.getInscripcion().getId()));
		Map<Long, List<String>> extrasPorInscripcion = new HashMap<>();
		extras.porInscripciones(ids).forEach(e -> extrasPorInscripcion
				.computeIfAbsent(e.getId().inscripcionId(), k -> new ArrayList<>()).add(e.getId().extra()));
		Map<Long, HotelPrevio> hotelPorInscripcion = porPersona(hoteles.porInscripciones(ids),
				HotelPrevio::getInscripcionId);
		Map<Long, List<Pago>> pagosPorInscripcion = pagos.porInscripciones(ids).stream()
				.collect(Collectors.groupingBy(p -> p.getInscripcion().getId()));

		List<Registration> registros = new ArrayList<>(lista.size());
		for (Inscripcion i : lista) {
			Integrante titular = null;
			Integrante acompanante = null;
			for (InscripcionParticipante p : participantesPorInscripcion.getOrDefault(i.getId(), List.of())) {
				Long personaId = p.getPersona().getId();
				Integrante integrante = new Integrante(p, p.getPersona(), fichaPorPersona.get(personaId),
						contactoPorPersona.get(personaId), consentimientoPorPersona.get(personaId));
				if (p.getRol() == RolParticipante.TITULAR) {
					titular = integrante;
				} else {
					acompanante = integrante;
				}
			}
			registros.add(mapper.aRegistration(new ReservaCompleta(i, titular, acompanante,
					extrasPorInscripcion.getOrDefault(i.getId(), List.of()), hotelPorInscripcion.get(i.getId()),
					pagosPorInscripcion.getOrDefault(i.getId(), List.of()))));
		}
		return registros;
	}

	private static <T> Map<Long, T> porPersona(Collection<T> valores, Function<T, Long> clave) {
		return valores.stream().collect(Collectors.toMap(clave, Function.identity()));
	}

}
