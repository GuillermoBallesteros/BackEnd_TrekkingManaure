package co.manaure.trekking.reserva;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import co.manaure.trekking.TestcontainersConfiguration;
import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.Auditoria;
import co.manaure.trekking.auditoria.AuditoriaRepository;
import co.manaure.trekking.auditoria.EntidadAuditoria;
import co.manaure.trekking.pago.ConceptoPago;
import co.manaure.trekking.pago.Pago;
import co.manaure.trekking.pago.PagoRepository;
import co.manaure.trekking.pago.RevisionPago;
import co.manaure.trekking.persona.ConsentimientoPersona;
import co.manaure.trekking.persona.ConsentimientoPersonaRepository;
import co.manaure.trekking.persona.ContactoEmergencia;
import co.manaure.trekking.persona.ContactoEmergenciaRepository;
import co.manaure.trekking.persona.Persona;
import co.manaure.trekking.persona.PersonaRepository;

/** HU-N01: las entidades de reserva, pago y auditoría se guardan y leen contra el esquema real. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ModeloReservaPersistenciaTest {

	@Autowired GeneradorCodigos codigos;
	@Autowired PersonaRepository personas;
	@Autowired ContactoEmergenciaRepository contactos;
	@Autowired ConsentimientoPersonaRepository consentimientos;
	@Autowired InscripcionRepository inscripciones;
	@Autowired InscripcionParticipanteRepository participantes;
	@Autowired InscripcionExtraRepository extras;
	@Autowired HotelPrevioRepository hoteles;
	@Autowired PagoRepository pagos;
	@Autowired AuditoriaRepository auditorias;
	@Autowired JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		for (String tabla : new String[] { "auditoria", "pago", "hotel_previo", "inscripcion_extra",
				"inscripcion_participante", "inscripcion", "consentimiento_persona", "contacto_emergencia", "persona" }) {
			jdbc.update("delete from " + tabla);
		}
	}

	@Test
	void secuenciasFormateanPERyPAGConCuatroDigitos() {
		assertThat(GeneradorCodigos.formatear("PER", 1)).isEqualTo("PER-0001");
		assertThat(GeneradorCodigos.formatear("PAG", 1)).isEqualTo("PAG-0001");
		assertThat(GeneradorCodigos.formatear("PER", 12345)).isEqualTo("PER-12345");

		String a = codigos.siguienteCodigoInscripcion();
		String b = codigos.siguienteCodigoInscripcion();
		assertThat(a).matches("PER-\\d{4,}");
		assertThat(Long.parseLong(b.substring(4))).isEqualTo(Long.parseLong(a.substring(4)) + 1);
		assertThat(codigos.siguienteCodigoPago()).matches("PAG-\\d{4,}");
	}

	@Test
	void guardaYLeeLaReservaCompleta() {
		Persona titular = personas.save(new Persona("Maria", "Perez", "CC", "DOC12345", LocalDate.of(1990, 5, 14),
				"Femenino", "CO", "3001234567", "maria@example.com", "Valledupar", "Otra", "Mi EPS"));
		contactos.save(new ContactoEmergencia(titular, "Luis Perez", "CO", "3157654321", "Hermano o hermana"));
		consentimientos.save(new ConsentimientoPersona(titular, "TYC-2026-10-28", OffsetDateTime.now(), true, true, true));

		Inscripcion insc = inscripciones.save(new Inscripcion(codigos.siguienteCodigoInscripcion(), PlanPago.APARTADO,
				LocalDate.of(2026, 11, 9), 350000, 50000, 150000, 350000, 150000, 200000, 45000, 500000,
				LocalDate.of(2026, 10, 28)));
		participantes.save(new InscripcionParticipante(insc, titular, RolParticipante.TITULAR, "Páramo", true, "M",
				"DOC12345"));
		extras.save(new InscripcionExtra(insc.getId(), "cuatrimoto"));
		hoteles.save(new HotelPrevio(insc, LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 14), true));
		Pago pago = pagos.save(Pago.publico(codigos.siguienteCodigoPago(), insc, ConceptoPago.SEPARACION_CUPO, 50000,
				LocalDate.of(2026, 10, 28), java.time.OffsetDateTime.now()));
		auditorias.save(new Auditoria(null, AccionAuditoria.INICIAR_SESION, EntidadAuditoria.SESION, "sesion", null));

		Inscripcion leida = inscripciones.findByCodigo(insc.getCodigo()).orElseThrow();
		assertThat(leida.getPlanPago()).isEqualTo(PlanPago.APARTADO);
		assertThat(leida.getTotal()).isEqualTo(500000L);
		assertThat(leida.isActiva()).isTrue();
		assertThat(leida.getFechaCompromiso()).isEqualTo(LocalDate.of(2026, 11, 9));
		assertThat(leida.getCreadoEn()).isNotNull();
		assertThat(participantes.findAll()).singleElement().satisfies(p -> {
			assertThat(p.getRol()).isEqualTo(RolParticipante.TITULAR);
			assertThat(p.getDocumentoActivo()).isEqualTo("DOC12345");
		});
		assertThat(extras.findAll()).singleElement().satisfies(e -> assertThat(e.getId().extra()).isEqualTo("cuatrimoto"));
		assertThat(hoteles.findAll()).singleElement().satisfies(h -> assertThat(h.isConAcompanante()).isTrue());
		Pago pagoLeido = pagos.findByCodigo(pago.getCodigo()).orElseThrow();
		assertThat(pagoLeido.getRevision()).isEqualTo(RevisionPago.POR_REVISAR);
		assertThat(pagos.findByInscripcionIdOrderByIdAsc(insc.getId())).hasSize(1);
		assertThat(auditorias.findAll()).singleElement()
				.satisfies(a -> assertThat(a.getAccion()).isEqualTo(AccionAuditoria.INICIAR_SESION));
	}

}
