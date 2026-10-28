package co.manaure.trekking.reserva;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.manaure.trekking.api.ReservaMapper;
import co.manaure.trekking.api.dto.NewRegistration;
import co.manaure.trekking.api.dto.Registration;
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
import co.manaure.trekking.persona.Persona;
import co.manaure.trekking.persona.PersonaRepository;
import co.manaure.trekking.regla.CalculadoraPrecios;
import co.manaure.trekking.regla.CatalogoReglas;
import co.manaure.trekking.regla.ContextoReserva;
import co.manaure.trekking.regla.ErrorRegla;
import co.manaure.trekking.regla.ParametrosEvento;
import co.manaure.trekking.regla.PreciosVigentes;
import co.manaure.trekking.regla.ValidadorPlan;
import co.manaure.trekking.regla.ValidadorReserva;
import co.manaure.trekking.regla.adaptador.CatalogoReglasAdaptador;
import co.manaure.trekking.regla.modelo.AcompananteRegla;
import co.manaure.trekking.regla.modelo.NuevaReserva;
import co.manaure.trekking.regla.modelo.PersonaRegla;
import co.manaure.trekking.reserva.ReservaCompleta.Integrante;

/**
 * Registro público de una reserva (HU-N04). Toma el bloqueo global de la configuración (diseño §9), valida con las
 * reglas del §8 y crea todo en una sola transacción: una validación fallida no deja filas. El total, el pago
 * inicial, el estado, los códigos y la fecha de aceptación los fija siempre el servidor.
 */
@Service
public class ReservaService {

	private final ConfiguracionService configuracion;
	private final CatalogoReglasAdaptador catalogos;
	private final InscripcionParticipanteRepository participantes;
	private final InscripcionRepository inscripciones;
	private final InscripcionExtraRepository extras;
	private final HotelPrevioRepository hoteles;
	private final PersonaRepository personas;
	private final FichaSaludRepository fichas;
	private final ContactoEmergenciaRepository contactos;
	private final ConsentimientoPersonaRepository consentimientos;
	private final PagoRepository pagos;
	private final GeneradorCodigos codigos;
	private final ReservaMapper mapper;
	private final Clock clock;

	public ReservaService(ConfiguracionService configuracion, CatalogoReglasAdaptador catalogos,
			InscripcionParticipanteRepository participantes, InscripcionRepository inscripciones,
			InscripcionExtraRepository extras, HotelPrevioRepository hoteles, PersonaRepository personas,
			FichaSaludRepository fichas, ContactoEmergenciaRepository contactos,
			ConsentimientoPersonaRepository consentimientos, PagoRepository pagos, GeneradorCodigos codigos,
			ReservaMapper mapper, Clock clock) {
		this.configuracion = configuracion;
		this.catalogos = catalogos;
		this.participantes = participantes;
		this.inscripciones = inscripciones;
		this.extras = extras;
		this.hoteles = hoteles;
		this.personas = personas;
		this.fichas = fichas;
		this.contactos = contactos;
		this.consentimientos = consentimientos;
		this.pagos = pagos;
		this.codigos = codigos;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional
	public Registration crear(NewRegistration entrada) {
		NuevaReserva reserva = mapper.aModelo(entrada);

		// Primero el bloqueo global: serializa la comprobación de documento y de cupo (diseño §9).
		ConfiguracionEvento cfg = configuracion.obtenerConBloqueo();
		LocalDate hoy = LocalDate.now(clock);
		CatalogoReglas catalogo = catalogos.catalogos();
		ParametrosEvento evento = new ParametrosEvento(cfg.getFechaApertura(), cfg.getFechaEventoInicio(),
				cfg.getFechaCierre(), cfg.getVersionTerminos());

		Optional<ErrorRegla> error = ValidadorReserva.validar(reserva,
				new ContextoReserva(catalogo, evento, hoy, documentosActivos(reserva), cuposLibres(cfg)));
		if (error.isPresent()) {
			throw NegocioException.de(error.get());
		}
		return mapper.aRegistration(guardar(reserva, cfg, catalogo, hoy));
	}

	private Set<String> documentosActivos(NuevaReserva reserva) {
		Set<String> documentos = new LinkedHashSet<>();
		documentos.add(reserva.titular().documento());
		if (reserva.acompanante() != null) {
			documentos.add(reserva.acompanante().persona().documento());
		}
		documentos.removeIf(d -> d == null || d.isEmpty());
		if (documentos.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(participantes.documentosActivos(documentos));
	}

	private int cuposLibres(ConfiguracionEvento cfg) {
		long ocupados = participantes.contarOcupados(-1L);
		return (int) Math.max(0, cfg.getCuposParamo() - ocupados);
	}

	private ReservaCompleta guardar(NuevaReserva reserva, ConfiguracionEvento cfg, CatalogoReglas catalogo,
			LocalDate hoy) {
		PreciosVigentes precios = new PreciosVigentes(cfg.getPrecioTitular(), cfg.getValorApartado(),
				cfg.getPrecioAcompananteSinKit(), cfg.getPrecioAcompananteConKit(), cfg.getPrecioCuatrimoto(),
				cfg.getPrecioParapente(), cfg.getPrecioHotelNoche());
		long total = CalculadoraPrecios.total(CalculadoraPrecios.Seleccion.de(reserva), precios);
		PlanPago plan = ValidadorPlan.PLAN_APARTADO.equals(reserva.plan()) ? PlanPago.APARTADO : PlanPago.TOTAL;
		LocalDate compromiso = plan == PlanPago.APARTADO ? LocalDate.parse(reserva.fechaCompromiso()) : null;
		// Milisegundos: es la precisión del JSON (ISO-8601) y así lo guardado coincide con lo devuelto.
		OffsetDateTime ahora = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);

		Inscripcion inscripcion = inscripciones.save(new Inscripcion(codigos.siguienteCodigoInscripcion(), plan,
				compromiso, precios.titular(), precios.apartado(), precios.acompananteSinKit(),
				precios.acompananteConKit(), precios.cuatrimoto(), precios.parapente(), precios.hotelNoche(), total,
				hoy));

		Integrante titular = integrante(inscripcion, reserva.titular(), RolParticipante.TITULAR, true,
				reserva.tallaTitular(), ahora);
		AcompananteRegla a = reserva.acompanante();
		Integrante acompanante = a == null ? null
				: integrante(inscripcion, a.persona(), RolParticipante.ACOMPANANTE, a.kit(), a.talla(), ahora);

		// Extras en el orden del catálogo, como los devuelve el JSON.
		List<String> elegidos = catalogo.extras().keySet().stream().filter(reserva.extras()::contains).toList();
		elegidos.forEach(e -> extras.save(new InscripcionExtra(inscripcion.getId(), e)));

		HotelPrevio hotel = reserva.hotel() == null ? null
				: hoteles.save(new HotelPrevio(inscripcion, LocalDate.parse(reserva.hotel().llegada()),
						LocalDate.parse(reserva.hotel().salida()), reserva.hotel().conAcompanante()));

		var inicial = CalculadoraPrecios.pagoInicial(plan, total, precios);
		Pago pago = pagos.save(Pago.publico(codigos.siguienteCodigoPago(), inscripcion, inicial.concepto(),
				inicial.monto(), hoy, ahora));

		return new ReservaCompleta(inscripcion, titular, acompanante, new ArrayList<>(elegidos), hotel,
				List.of(pago));
	}

	/** Persona, ficha de salud (cifrada al guardar), contacto, consentimiento y participante. */
	private Integrante integrante(Inscripcion inscripcion, PersonaRegla p, RolParticipante rol, boolean kit,
			String talla, OffsetDateTime ahora) {
		Persona persona = personas.save(new Persona(p.nombres(), p.apellidos(), p.tipoDocumento(), p.documento(),
				LocalDate.parse(p.fechaNacimiento()), p.genero(), p.telefono().pais(), p.telefono().numero(),
				p.correo(), p.ciudad(), p.eps().entidad(), p.eps().otra()));
		FichaSalud ficha = fichas.save(new FichaSalud(persona, p.salud().enfermedad(), p.salud().alergias(),
				p.salud().discapacidad(), p.salud().ejercita()));
		ContactoEmergencia contacto = contactos.save(new ContactoEmergencia(persona, p.contacto().nombre(),
				p.contacto().telefono().pais(), p.contacto().telefono().numero(), p.contacto().parentesco()));
		// La fecha de aceptación es la del servidor; la del cliente solo prueba que marcó la casilla.
		ConsentimientoPersona consentimiento = consentimientos.save(new ConsentimientoPersona(persona,
				p.consentimiento().version(), ahora, p.consentimiento().usoImagen(),
				p.compromisos().areasProtegidas(), p.compromisos().medioAmbiente()));
		InscripcionParticipante participante = participantes.save(new InscripcionParticipante(inscripcion, persona,
				rol, p.estadia(), kit, kit ? talla : null, p.documento()));
		return new Integrante(participante, persona, ficha, contacto, consentimiento);
	}

}
