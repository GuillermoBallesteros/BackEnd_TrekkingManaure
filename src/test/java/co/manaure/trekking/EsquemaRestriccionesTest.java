package co.manaure.trekking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** HU-N01: catálogos sembrados, configuración de una sola fila y restricciones CHECK/UNIQUE (PostgreSQL 18 real). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class EsquemaRestriccionesTest {

	private static final AtomicInteger CONTADOR = new AtomicInteger(9000);

	@Autowired
	JdbcTemplate jdbc;

	@AfterEach
	void limpiar() {
		for (String tabla : List.of("pago", "hotel_previo", "inscripcion_extra", "inscripcion_participante",
				"inscripcion", "consentimiento_persona", "contacto_emergencia", "ficha_salud", "persona")) {
			jdbc.update("delete from " + tabla);
		}
	}

	// ---------- ayudas ----------

	/** pgjdbc devuelve int2 como Integer; se compara por valor. */
	private static long num(Map<String, Object> fila, String columna) {
		return ((Number) fila.get(columna)).longValue();
	}

	private static String lit(Object v) {
		return v == null ? "NULL" : "'" + v + "'";
	}

	private long insertarPersona() {
		int n = CONTADOR.incrementAndGet();
		return jdbc.queryForObject("""
				insert into persona (nombres, apellidos, tipo_documento, documento, fecha_nacimiento, genero,
				    telefono_pais, telefono_numero, correo, ciudad, eps, eps_otra)
				values ('Maria', 'Perez', 'CC', 'DOC%d', date '1990-05-14', 'Femenino', 'CO', '3001234567',
				    'maria@example.com', 'Valledupar', 'Nueva EPS', null) returning id""".formatted(n), Long.class);
	}

	private long insertarInscripcion(String plan, String fechaCompromiso) {
		int n = CONTADOR.incrementAndGet();
		return jdbc.queryForObject("""
				insert into inscripcion (codigo, plan_pago, fecha_compromiso, precio_titular_aplicado,
				    precio_apartado_aplicado, precio_acompanante_sin_kit_aplicado, precio_acompanante_con_kit_aplicado,
				    precio_cuatrimoto_aplicado, precio_parapente_aplicado, precio_hotel_noche_aplicado, total,
				    fecha_registro)
				values ('PER-%d', '%s', %s, 350000, 50000, 150000, 350000, 150000, 200000, 45000, 350000,
				    date '2026-10-28') returning id""".formatted(n, plan, fechaCompromiso), Long.class);
	}

	private void insertarParticipante(long inscripcionId, long personaId, String rol, boolean kit, String talla,
			String documentoActivo) {
		jdbc.update("""
				insert into inscripcion_participante (inscripcion_id, persona_id, rol, estadia, lleva_kit, talla,
				    documento_activo)
				values (%d, %d, '%s', 'Páramo', %s, %s, %s)""".formatted(inscripcionId, personaId, rol, kit, lit(talla),
				lit(documentoActivo)));
	}

	private void insertarPago(long inscripcionId, String revision, String motivo, String revisadoEn) {
		int n = CONTADOR.incrementAndGet();
		jdbc.update("""
				insert into pago (codigo, inscripcion_id, concepto, monto, fecha, revision, motivo_rechazo, origen,
				    revisado_en)
				values ('PAG-%d', %d, 'SEPARACION_CUPO', 50000, date '2026-10-28', '%s', %s, 'PUBLICO', %s)"""
				.formatted(n, inscripcionId, revision, lit(motivo), revisadoEn));
	}

	// ---------- catálogos ----------

	@Test
	void tiposDocumentoSembradosConPatronYEdades() {
		assertThat(jdbc.queryForObject("select count(*) from tipo_documento", Integer.class)).isEqualTo(6);
		Map<String, Object> cc = jdbc.queryForMap("select * from tipo_documento where clave = 'CC'");
		assertThat(cc).containsEntry("patron", "[0-9]{6,10}");
		assertThat(num(cc, "edad_min")).isEqualTo(18);
		assertThat(cc.get("edad_max")).isNull();
		Map<String, Object> ti = jdbc.queryForMap("select * from tipo_documento where clave = 'TI'");
		assertThat(num(ti, "edad_min")).isEqualTo(7);
		assertThat(num(ti, "edad_max")).isEqualTo(18);
		Map<String, Object> rc = jdbc.queryForMap("select * from tipo_documento where clave = 'RC'");
		assertThat(rc.get("edad_min")).isNull();
		assertThat(num(rc, "edad_max")).isEqualTo(7);
		assertThat(jdbc.queryForList("select clave from tipo_documento order by orden", String.class))
				.containsExactly("CC", "CE", "TI", "RC", "Pasaporte", "PPT");
	}

	@Test
	void generosTallasYEstadiasSembrados() {
		assertThat(jdbc.queryForObject("select count(*) from genero", Integer.class)).isEqualTo(4);
		assertThat(jdbc.queryForObject("select count(*) from talla", Integer.class)).isEqualTo(7);
		assertThat(jdbc.queryForObject("select count(*) from estadia", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForList("select clave from estadia where ocupa_cupo", String.class))
				.containsExactly("Páramo");
	}

	@Test
	void extrasEpsYParentescosSembradosConSusBanderas() {
		assertThat(jdbc.queryForObject("select count(*) from extra", Integer.class)).isEqualTo(3);
		assertThat(jdbc.queryForList("select clave from extra where requiere_acompanante", String.class))
				.containsExactly("parapenteAcompanante");
		assertThat(jdbc.queryForObject("select count(*) from eps", Integer.class)).isEqualTo(25);
		assertThat(jdbc.queryForList("select clave from eps where pide_nombre", String.class)).containsExactly("Otra");
		assertThat(jdbc.queryForObject("select count(*) from parentesco", Integer.class)).isEqualTo(11);
		assertThat(jdbc.queryForList("select clave from parentesco where es_responsable_legal order by orden",
				String.class)).containsExactly("Madre", "Padre", "Tutor legal o acudiente");
	}

	@Test
	void trecePConReglasDeMovilYFijo() {
		assertThat(jdbc.queryForObject("select count(*) from pais_telefono", Integer.class)).isEqualTo(13);
		assertThat(jdbc.queryForList("select iso from pais_telefono where fijo_largos is null order by orden",
				String.class)).containsExactly("MX", "US", "AR", "DO");
		Map<String, Object> co = jdbc.queryForMap("""
				select indicativo, movil_largos::text as ml, movil_inicios::text as mi, fijo_largos::text as fl,
				       fijo_inicios::text as fi from pais_telefono where iso = 'CO'""");
		assertThat(co).containsEntry("indicativo", "57").containsEntry("ml", "{10}").containsEntry("mi", "{3}")
				.containsEntry("fl", "{10}").containsEntry("fi", "{60}");
		assertThat(jdbc.queryForObject("select movil_largos::text from pais_telefono where iso = 'AR'", String.class))
				.isEqualTo("{10,11}");
		assertThat(jdbc.queryForObject("select movil_inicios::text from pais_telefono where iso = 'DO'", String.class))
				.isEqualTo("{809,829,849}");
	}

	// ---------- configuración ----------

	@Test
	void configuracionTieneUnaFilaConLosValoresIniciales() {
		assertThat(jdbc.queryForObject("select count(*) from configuracion_evento", Integer.class)).isEqualTo(1);
		Map<String, Object> c = jdbc.queryForMap("select * from configuracion_evento");
		assertThat(num(c, "id")).isEqualTo(1);
		assertThat(c).containsEntry("nombre_evento", "Trekking Desafío Perijá 13K")
				.containsEntry("cupos_paramo", 60)
				.containsEntry("precio_titular", 350000L)
				.containsEntry("valor_apartado", 50000L)
				.containsEntry("precio_acompanante_sin_kit", 150000L)
				.containsEntry("precio_acompanante_con_kit", 350000L)
				.containsEntry("precio_cuatrimoto", 150000L)
				.containsEntry("precio_parapente", 200000L)
				.containsEntry("precio_hotel_noche", 45000L)
				.containsEntry("version_terminos", "TYC-2026-10-28");
		assertThat(c.get("fecha_evento_inicio").toString()).isEqualTo("2026-11-15");
		assertThat(c.get("fecha_evento_fin").toString()).isEqualTo("2026-11-16");
		assertThat(c.get("fecha_apertura").toString()).isEqualTo("2026-10-28");
		assertThat(c.get("fecha_cierre").toString()).isEqualTo("2026-11-09");
	}

	@Test
	void configuracionRechazaUnaSegundaFila() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into configuracion_evento (id, nombre_evento, fecha_evento_inicio, fecha_evento_fin,
				    fecha_apertura, fecha_cierre, cupos_paramo, precio_titular, valor_apartado,
				    precio_acompanante_sin_kit, precio_acompanante_con_kit, precio_cuatrimoto, precio_parapente,
				    precio_hotel_noche, version_terminos)
				values (2, 'Otro', date '2026-11-15', date '2026-11-16', date '2026-10-28', date '2026-11-09',
				    60, 350000, 50000, 150000, 350000, 150000, 200000, 45000, 'T')"""))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_configuracion_unica");
	}

	// ---------- restricciones ----------

	@Test
	void titularConLlevaKitFalsoFalla() {
		long insc = insertarInscripcion("TOTAL", "NULL");
		long persona = insertarPersona();
		assertThatThrownBy(() -> insertarParticipante(insc, persona, "TITULAR", false, null, null))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_participante_titular_kit");
	}

	@Test
	void kitSinTallaOSinKitConTallaFalla() {
		long insc = insertarInscripcion("TOTAL", "NULL");
		long titular = insertarPersona();
		insertarParticipante(insc, titular, "TITULAR", true, "M", null);
		long acompanante = insertarPersona();
		assertThatThrownBy(() -> insertarParticipante(insc, acompanante, "ACOMPANANTE", true, null, null))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_participante_talla");
		assertThatThrownBy(() -> insertarParticipante(insc, acompanante, "ACOMPANANTE", false, "M", null))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_participante_talla");
	}

	@Test
	void documentoActivoRepetidoFallaYNulosCoexisten() {
		long insc1 = insertarInscripcion("TOTAL", "NULL");
		long insc2 = insertarInscripcion("TOTAL", "NULL");
		insertarParticipante(insc1, insertarPersona(), "TITULAR", true, "M", "12345678");
		assertThatThrownBy(() -> insertarParticipante(insc2, insertarPersona(), "TITULAR", true, "M", "12345678"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uq_participante_documento_activo");

		insertarParticipante(insc2, insertarPersona(), "TITULAR", true, "M", null);
		long insc3 = insertarInscripcion("TOTAL", "NULL");
		insertarParticipante(insc3, insertarPersona(), "TITULAR", true, "M", null);
		assertThat(jdbc.queryForObject(
				"select count(*) from inscripcion_participante where documento_activo is null", Integer.class))
				.isEqualTo(2);
	}

	@Test
	void pagoRechazadoSinMotivoFalla() {
		long insc = insertarInscripcion("TOTAL", "NULL");
		assertThatThrownBy(() -> insertarPago(insc, "RECHAZADO", null, "now()"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_pago_motivo");
	}

	@Test
	void pagoPorRevisarConRevisadoEnFalla() {
		long insc = insertarInscripcion("TOTAL", "NULL");
		assertThatThrownBy(() -> insertarPago(insc, "POR_REVISAR", null, "now()"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_pago_revisado");
		// Un pago público nuevo (POR_REVISAR sin revisión) sí es válido.
		insertarPago(insc, "POR_REVISAR", null, "NULL");
	}

	@Test
	void planApartadoSinFechaOTotalConFechaFalla() {
		assertThatThrownBy(() -> insertarInscripcion("APARTADO", "NULL"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_inscripcion_fecha_compromiso");
		assertThatThrownBy(() -> insertarInscripcion("TOTAL", "date '2026-11-09'"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_inscripcion_fecha_compromiso");
		insertarInscripcion("APARTADO", "date '2026-11-09'");
	}

	@Test
	void fichaSaludConBanderaYDetalleNuloFalla() {
		long persona = insertarPersona();
		assertThatThrownBy(() -> jdbc.update("""
				insert into ficha_salud (persona_id, tiene_enfermedad, tiene_alergias, tiene_discapacidad,
				    enfermedad_cifrada, practica_deporte)
				values (%d, true, false, false, null, true)""".formatted(persona)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_ficha_salud_enfermedad");
	}

}
