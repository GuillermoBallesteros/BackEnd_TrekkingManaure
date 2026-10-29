package co.manaure.trekking.notificacion;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import co.manaure.trekking.notificacion.ContextoAviso.PagoAviso;
import co.manaure.trekking.notificacion.ContextoAviso.PersonaAviso;
import co.manaure.trekking.pago.RevisionPago;

/**
 * Decide, reclama y envía los avisos de RN-24 (diseño §18). Sin transacción propia: lee con {@link LectorAvisos},
 * reclama y marca con {@link RegistroEnvios} y llama a Brevo entre medias. Los logs solo llevan el código de la
 * reserva, el tipo y la clase del error: nunca correos ni nombres.
 */
@Service
@ConditionalOnProperty(name = "app.brevo.activo", havingValue = "true")
public class ServicioAvisos {

	private static final Logger log = LoggerFactory.getLogger(ServicioAvisos.class);

	private final LectorAvisos lector;
	private final RegistroEnvios registro;
	private final ClienteBrevo cliente;
	private final PropiedadesBrevo propiedades;
	private final Clock clock;
	private final Set<TipoAviso> sinPlantillaAvisados = ConcurrentHashMap.newKeySet();

	public ServicioAvisos(LectorAvisos lector, RegistroEnvios registro, ClienteBrevo cliente,
			PropiedadesBrevo propiedades, Clock clock) {
		this.lector = lector;
		this.registro = registro;
		this.cliente = cliente;
		this.propiedades = propiedades;
		this.clock = clock;
	}

	/** Recordatorios de hoy para una inscripción (tarea diaria). @return avisos enviados */
	public int recordar(long inscripcionId, LocalDate hoy) {
		Optional<ContextoAviso> leido = lector.porInscripcion(inscripcionId, hoy);
		if (leido.isEmpty()) {
			return 0;
		}
		ContextoAviso ctx = leido.get();
		int enviados = 0;
		for (Aviso aviso : DecisorAvisos.recordatorios(ctx.estado(), ctx.fechaCompromiso(), ctx.inicioEvento(), hoy,
				ctx.personas().keySet())) {
			if (enviar(ctx, aviso, null)) {
				enviados++;
			}
		}
		return enviados;
	}

	/**
	 * Aviso de una revisión ya confirmada. Si la revisión del pago cambió entre el commit y esta lectura, no se avisa
	 * del estado anterior.
	 */
	public boolean avisarRevision(String codigoPago, RevisionPago revision) {
		Optional<Aviso> aviso = DecisorAvisos.porRevision(revision, codigoPago);
		if (aviso.isEmpty()) {
			return false;
		}
		Optional<ContextoAviso> leido = lector.porPago(codigoPago, LocalDate.now(clock));
		if (leido.isEmpty()) {
			return false;
		}
		ContextoAviso ctx = leido.get();
		Optional<PagoAviso> pago = ctx.pago(codigoPago);
		if (pago.isEmpty() || pago.get().revision() != revision) {
			return false;
		}
		return enviar(ctx, aviso.get(), pago.get());
	}

	private boolean enviar(ContextoAviso ctx, Aviso aviso, PagoAviso pago) {
		PersonaAviso persona = ctx.personas().get(aviso.destinatario());
		if (persona == null || persona.correo() == null || persona.correo().isBlank()) {
			return false;
		}
		long plantilla = propiedades.plantilla(aviso.tipo());
		if (plantilla <= 0) {
			if (sinPlantillaAvisados.add(aviso.tipo())) {
				log.warn("Avisos: {} no tiene plantilla configurada; se omite.", aviso.tipo());
			}
			return false;
		}
		Optional<Long> reclamo = registro.reclamar(ctx.inscripcionId(), aviso);
		if (reclamo.isEmpty()) {
			return false;
		}
		try {
			String mensaje = cliente.enviar(persona.destinatario(), plantilla, parametros(ctx, persona, pago),
					aviso.tipo());
			registro.marcarEnviado(reclamo.get(), mensaje);
			return true;
		}
		catch (RuntimeException ex) {
			registro.marcarFallido(reclamo.get());
			log.warn("Avisos: no se pudo enviar {} de la reserva {} ({}{}).", aviso.tipo(), ctx.codigoReserva(),
					ex.getClass().getSimpleName(), ex instanceof ErrorBrevo e ? " " + e.estado() : "");
			return false;
		}
	}

	/** Parámetros de la plantilla ({@code {{ params.x }}}); sin documento, EPS ni salud. */
	static Map<String, Object> parametros(ContextoAviso ctx, PersonaAviso persona, PagoAviso pago) {
		Map<String, Object> p = new LinkedHashMap<>();
		p.put("nombre", FormatoAviso.nombrePropio(persona.nombres()));
		p.put("codigoReserva", ctx.codigoReserva());
		p.put("total", FormatoAviso.dinero(ctx.total()));
		p.put("pagado", FormatoAviso.dinero(ctx.pagado()));
		p.put("saldo", FormatoAviso.dinero(ctx.saldo()));
		p.put("pagoCompleto", ctx.saldo() == 0);
		p.put("fechaCompromiso", FormatoAviso.fecha(ctx.fechaCompromiso()));
		p.put("evento", ctx.nombreEvento() == null ? "" : ctx.nombreEvento());
		p.put("fechaEvento", FormatoAviso.fecha(ctx.inicioEvento()));
		if (pago != null) {
			p.put("codigoPago", pago.codigo());
			p.put("concepto", FormatoAviso.concepto(pago.concepto()));
			p.put("monto", FormatoAviso.dinero(pago.monto()));
			p.put("motivo", pago.motivo() == null ? "" : pago.motivo());
		}
		return p;
	}

}
