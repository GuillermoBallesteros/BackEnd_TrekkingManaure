package co.manaure.trekking.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import co.manaure.trekking.api.dto.Companion;
import co.manaure.trekking.api.dto.NewCompanion;
import co.manaure.trekking.api.dto.NewRegistration;
import co.manaure.trekking.api.dto.Registration;
import co.manaure.trekking.api.dto.TiposReserva.Commitments;
import co.manaure.trekking.api.dto.TiposReserva.Consent;
import co.manaure.trekking.api.dto.TiposReserva.EmergencyContact;
import co.manaure.trekking.api.dto.TiposReserva.Eps;
import co.manaure.trekking.api.dto.TiposReserva.Health;
import co.manaure.trekking.api.dto.TiposReserva.Payment;
import co.manaure.trekking.api.dto.TiposReserva.Phone;
import co.manaure.trekking.api.dto.TiposReserva.PreEventHotel;
import co.manaure.trekking.pago.ConceptoPago;
import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.regla.ValidadorCorreo;
import co.manaure.trekking.regla.modelo.AcompananteRegla;
import co.manaure.trekking.regla.modelo.Compromisos;
import co.manaure.trekking.regla.modelo.Consentimiento;
import co.manaure.trekking.regla.modelo.ContactoRegla;
import co.manaure.trekking.regla.modelo.EpsElegida;
import co.manaure.trekking.regla.modelo.HotelSolicitado;
import co.manaure.trekking.regla.modelo.NuevaReserva;
import co.manaure.trekking.regla.modelo.PersonaRegla;
import co.manaure.trekking.regla.modelo.SaludRegla;
import co.manaure.trekking.regla.modelo.Telefono;
import co.manaure.trekking.reserva.PlanPago;
import co.manaure.trekking.reserva.ReservaCompleta;
import co.manaure.trekking.reserva.ReservaCompleta.Integrante;

/**
 * Traduce entre el JSON del contrato (nombres en inglés) y el dominio (diseño §7). Sin estado ni lógica de
 * negocio. {@link #aRegistration(ReservaCompleta)} lo reutiliza la lista del panel (HU-N07).
 */
@Component
public class ReservaMapper {

	private static final DateTimeFormatter INSTANTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
			.withZone(ZoneOffset.UTC);

	// --- entrada: JSON -> modelo de reglas

	/**
	 * Normaliza antes de validar (diseño §8): el documento en mayúsculas (se valida y se guarda así, sin recortar
	 * espacios) y el correo en minúsculas.
	 */
	public NuevaReserva aModelo(NewRegistration r) {
		PersonaRegla titular = persona(r.firstName(), r.lastName(), r.documentType(), r.document(), r.birthDate(),
				r.gender(), r.phone(), r.email(), r.city(), r.eps(), r.emergencyContact(), r.health(),
				r.commitments(), r.consent(), r.stay());
		NewCompanion c = r.companion();
		AcompananteRegla acompanante = c == null ? null
				: new AcompananteRegla(
						persona(c.firstName(), c.lastName(), c.documentType(), c.document(), c.birthDate(),
								c.gender(), c.phone(), c.email(), c.city(), c.eps(), c.emergencyContact(),
								c.health(), c.commitments(), c.consent(), c.stay()),
						Boolean.TRUE.equals(c.kit()), c.shirtSize());
		HotelSolicitado hotel = r.preEventHotel() == null ? null
				: new HotelSolicitado(r.preEventHotel().checkIn(), r.preEventHotel().checkOut(),
						Boolean.TRUE.equals(r.preEventHotel().withCompanion()));
		return new NuevaReserva(titular, r.shirtSize(), acompanante, List.copyOf(r.extras()), hotel, r.plan(),
				r.commitmentDate());
	}

	private static PersonaRegla persona(String nombres, String apellidos, String tipoDocumento, String documento,
			String nacimiento, String genero, Phone telefono, String correo, String ciudad, Eps eps,
			EmergencyContact contacto, Health salud, Commitments compromisos, Consent consentimiento,
			String estadia) {
		return new PersonaRegla(nombres, apellidos, tipoDocumento, mayusculas(documento), nacimiento, genero,
				new Telefono(telefono.country(), telefono.number()), ValidadorCorreo.normalizar(correo), ciudad,
				new EpsElegida(eps.entity(), eps.other()),
				new ContactoRegla(contacto.name(), new Telefono(contacto.phone().country(), contacto.phone().number()),
						contacto.relationship()),
				new SaludRegla(salud.illness(), salud.allergies(), salud.disability(), salud.exercises()),
				new Compromisos(compromisos.protectedAreas(), compromisos.environment()),
				new Consentimiento(consentimiento.acceptedAt(), consentimiento.version(), consentimiento.imageUse()),
				estadia);
	}

	private static String mayusculas(String documento) {
		return documento == null ? null : documento.toUpperCase(Locale.ROOT);
	}

	// --- salida: dominio -> JSON

	public Registration aRegistration(ReservaCompleta r) {
		Integrante t = r.titular();
		var insc = r.inscripcion();
		var p = t.persona();
		return new Registration(insc.getCodigo(), insc.getFechaRegistro().toString(), p.getNombres(),
				p.getApellidos(), p.getTipoDocumento(), p.getDocumento(), p.getFechaNacimiento().toString(),
				p.getGenero(), telefono(p.getTelefonoPais(), p.getTelefonoNumero()), p.getCorreo(), p.getCiudad(),
				eps(t), contacto(t), salud(t), compromisos(t), consentimiento(t), t.participante().getEstadia(),
				t.participante().getTalla(), acompanante(r.acompanante()), List.copyOf(r.extras()), hotel(r),
				plan(insc.getPlanPago()),
				insc.getFechaCompromiso() == null ? null : insc.getFechaCompromiso().toString(),
				insc.isAutorizacionMenor(), r.pagos().stream().map(ReservaMapper::pago).toList(), insc.getTotal());
	}

	private Companion acompanante(Integrante a) {
		if (a == null) {
			return null;
		}
		var p = a.persona();
		return new Companion(p.getNombres(), p.getApellidos(), p.getTipoDocumento(), p.getDocumento(),
				p.getFechaNacimiento().toString(), p.getGenero(), telefono(p.getTelefonoPais(), p.getTelefonoNumero()),
				p.getCorreo(), p.getCiudad(), eps(a), contacto(a), salud(a), compromisos(a), consentimiento(a),
				a.participante().getEstadia(), a.participante().isLlevaKit(), a.participante().getTalla());
	}

	private static Phone telefono(String pais, String numero) {
		return new Phone(pais, numero);
	}

	private static Eps eps(Integrante i) {
		return new Eps(i.persona().getEps(), i.persona().getEpsOtra());
	}

	private static EmergencyContact contacto(Integrante i) {
		var c = i.contacto();
		return new EmergencyContact(c.getNombre(), telefono(c.getTelefonoPais(), c.getTelefonoNumero()),
				c.getParentesco());
	}

	private static Health salud(Integrante i) {
		var f = i.ficha();
		return new Health(f.getEnfermedad(), f.getAlergias(), f.getDiscapacidad(), f.isPracticaDeporte());
	}

	private static Commitments compromisos(Integrante i) {
		return new Commitments(i.consentimiento().isCompromisoAreasProtegidas(),
				i.consentimiento().isCompromisoMedioAmbiente());
	}

	private static Consent consentimiento(Integrante i) {
		var c = i.consentimiento();
		return new Consent(c.getVersionTerminos(), instante(c.getAceptadoEn()), c.isUsoImagen());
	}

	private static String instante(OffsetDateTime valor) {
		return INSTANTE.format(valor);
	}

	private static PreEventHotel hotel(ReservaCompleta r) {
		var h = r.hotel();
		return h == null ? null
				: new PreEventHotel(h.getEntrada().toString(), h.getSalida().toString(), h.isConAcompanante());
	}

	private static String plan(PlanPago plan) {
		return plan == PlanPago.TOTAL ? "total" : "apartado";
	}

	public static Payment pago(Pago p) {
		return new Payment(p.getCodigo(), concepto(p.getConcepto()), p.getMonto(), p.getFecha().toString(),
				revision(p.getRevision()), p.getMotivoRechazo());
	}

	private static String concepto(ConceptoPago c) {
		return switch (c) {
			case SEPARACION_CUPO -> "Separación de cupo";
			case PAGO_TOTAL -> "Pago total";
			case PAGO_SALDO -> "Pago de saldo";
		};
	}

	private static String revision(RevisionPago r) {
		return switch (r) {
			case POR_REVISAR -> "Por revisar";
			case APROBADO -> "Aprobado";
			case RECHAZADO -> "Rechazado";
		};
	}

}
