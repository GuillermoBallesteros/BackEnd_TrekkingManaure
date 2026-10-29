package co.manaure.trekking.regla;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.manaure.trekking.regla.CatalogoReglas.ReglaDocumento;
import co.manaure.trekking.regla.CatalogoReglas.ReglaNumero;
import co.manaure.trekking.regla.CatalogoReglas.ReglaPais;
import co.manaure.trekking.regla.modelo.AcompananteRegla;
import co.manaure.trekking.regla.modelo.Compromisos;
import co.manaure.trekking.regla.modelo.Consentimiento;
import co.manaure.trekking.regla.modelo.ContactoRegla;
import co.manaure.trekking.regla.modelo.EpsElegida;
import co.manaure.trekking.regla.modelo.NuevaReserva;
import co.manaure.trekking.regla.modelo.PersonaRegla;
import co.manaure.trekking.regla.modelo.SaludRegla;
import co.manaure.trekking.regla.modelo.Telefono;

/** Catálogos y datos válidos de prueba, copiados de {@code shared/reservas/data/catalogos.ts} del front. */
final class Datos {

	static final LocalDate HOY = LocalDate.parse("2026-10-28");
	static final ParametrosEvento EVENTO = new ParametrosEvento(LocalDate.parse("2026-10-28"),
			LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-09"), "TYC-2026-10-28");
	static final PreciosVigentes PRECIOS = new PreciosVigentes(350_000, 50_000, 150_000, 350_000, 150_000,
			200_000, 45_000);
	static final CatalogoReglas CATALOGOS = catalogos();

	private Datos() {
	}

	private static ReglaPais pais(String iso, String nombre, String indicativo, String ejemplo, int[] movilLargos,
			String[] movilInicios, int[] fijoLargos, String[] fijoInicios) {
		ReglaNumero fijo = fijoLargos == null ? null : new ReglaNumero(lista(fijoLargos), List.of(fijoInicios));
		return new ReglaPais(iso, nombre, indicativo, ejemplo, new ReglaNumero(lista(movilLargos), List.of(movilInicios)),
				fijo);
	}

	private static List<Integer> lista(int[] valores) {
		return java.util.Arrays.stream(valores).boxed().toList();
	}

	private static CatalogoReglas catalogos() {
		Map<String, ReglaDocumento> docs = new LinkedHashMap<>();
		docs.put("CC", new ReglaDocumento("[1-9][0-9]{7,9}", "Entre 8 y 10 dígitos", 18, null));
		docs.put("CE", new ReglaDocumento("[0-9]{6,10}", "Entre 6 y 10 dígitos", null, null));
		docs.put("TI", new ReglaDocumento("[0-9]{10,11}", "10 u 11 dígitos", 7, 18));
		docs.put("RC", new ReglaDocumento("[0-9]{10}", "10 dígitos", null, 7));
		docs.put("Pasaporte", new ReglaDocumento("[A-Za-z0-9]{6,12}", "Entre 6 y 12 letras o números", null, null));
		docs.put("PPT", new ReglaDocumento("[0-9]{5,8}", "Entre 5 y 8 dígitos", null, null));

		Map<String, Boolean> estadias = new LinkedHashMap<>();
		estadias.put("Páramo", true);
		estadias.put("Hotel en Manaure", false);
		estadias.put("Regreso a casa", false);

		Map<String, Boolean> extras = new LinkedHashMap<>();
		extras.put("cuatrimoto", false);
		extras.put("parapente", false);
		extras.put("parapenteAcompanante", true);

		Map<String, Boolean> eps = new LinkedHashMap<>();
		for (String e : List.of("Nueva EPS", "EPS Sura", "EPS Sanitas", "Salud Total", "Compensar", "Famisanar",
				"Coosalud", "Mutual SER", "Cajacopi", "Asmet Salud", "Emssanar", "Savia Salud", "Capresoca",
				"Comfenalco Valle", "Capital Salud", "Aliansalud", "Dusakawi", "Anas Wayuu", "Pijaos Salud",
				"Mallamas", "Comfachocó", "Comfaoriente", "Salud Mía", "SOS (Servicio Occidental de Salud)",
				"EPS Familiar de Colombia", "AIC (Asociación Indígena del Cauca)", "Régimen especial o de excepción",
				"No tengo EPS")) {
			eps.put(e, false);
		}
		eps.put("Otra", true);

		Map<String, Boolean> parentescos = new LinkedHashMap<>();
		parentescos.put("Madre", true);
		parentescos.put("Padre", true);
		parentescos.put("Tutor legal o acudiente", true);
		for (String p : List.of("Cónyuge o pareja", "Hijo o hija", "Hermano o hermana", "Abuelo o abuela",
				"Tío o tía", "Primo o prima", "Otro familiar", "Amigo o amiga")) {
			parentescos.put(p, false);
		}

		String[] uno9 = { "1", "2", "3", "4", "5", "6", "7", "8", "9" };
		Map<String, ReglaPais> paises = new LinkedHashMap<>();
		for (ReglaPais p : List.of(
				pais("CO", "Colombia", "57", "300 123 4567", new int[] { 10 }, new String[] { "3" },
						new int[] { 10 }, new String[] { "601", "602", "604", "605", "606", "607", "608" }),
				pais("VE", "Venezuela", "58", "412 123 4567", new int[] { 10 }, new String[] { "41", "42" },
						new int[] { 10 }, new String[] { "2" }),
				pais("EC", "Ecuador", "593", "99 123 4567", new int[] { 9 }, new String[] { "9" }, new int[] { 8 },
						new String[] { "2", "3", "4", "5", "6", "7" }),
				pais("PE", "Perú", "51", "912 345 678", new int[] { 9 }, new String[] { "9" }, new int[] { 8 },
						new String[] { "1", "4", "5", "6", "7", "8" }),
				pais("MX", "México", "52", "55 1234 5678", new int[] { 10 }, uno9, null, null),
				pais("US", "Estados Unidos", "1", "212 555 0123", new int[] { 10 },
						new String[] { "2", "3", "4", "5", "6", "7", "8", "9" }, null, null),
				pais("ES", "España", "34", "612 34 56 78", new int[] { 9 }, new String[] { "6", "7" },
						new int[] { 9 }, new String[] { "8", "9" }),
				pais("AR", "Argentina", "54", "9 11 1234 5678", new int[] { 10, 11 },
						new String[] { "1", "2", "3", "9" }, null, null),
				pais("CL", "Chile", "56", "9 1234 5678", new int[] { 9 }, new String[] { "9" }, new int[] { 9 },
						new String[] { "2", "3", "4", "5", "6", "7" }),
				pais("BR", "Brasil", "55", "11 91234 5678", new int[] { 11 }, uno9, new int[] { 10 }, uno9),
				pais("PA", "Panamá", "507", "6123 4567", new int[] { 8 }, new String[] { "6" }, new int[] { 7 },
						new String[] { "2", "3", "4", "5", "7", "8", "9" }),
				pais("CR", "Costa Rica", "506", "8312 3456", new int[] { 8 }, new String[] { "5", "6", "7", "8" },
						new int[] { 8 }, new String[] { "2", "4" }),
				pais("DO", "República Dominicana", "1", "809 123 4567", new int[] { 10 },
						new String[] { "809", "829", "849" }, null, null))) {
			paises.put(p.iso(), p);
		}

		return new CatalogoReglas(docs, List.of("Femenino", "Masculino", "Otro", "Prefiero no decirlo"),
				List.of("XS", "S", "M", "L", "XL", "XXL", "XXXL"), estadias, extras, eps, parentescos, paises);
	}

	static PersonaRegla titular() {
		return new PersonaRegla("ANA MARIA", "PEREZ GOMEZ", "CC", "1065123456", "1990-05-14", "Femenino",
				new Telefono("CO", "3001234567"), "ana@example.com", "Valledupar",
				new EpsElegida("Nueva EPS", null),
				new ContactoRegla("LUIS PEREZ", new Telefono("CO", "3157654321"), "Hermano o hermana"),
				new SaludRegla(null, "Penicilina", null, true), new Compromisos(true, true),
				new Consentimiento("2026-10-28T15:20:11.482Z", "TYC-2026-10-28", true), "Páramo");
	}

	static PersonaRegla persona() {
		return new PersonaRegla("CARLOS", "ROJAS DIAZ", "CC", "1065999888", "1992-03-01", "Masculino",
				new Telefono("CO", "3109998877"), "carlos@example.com", "Bogota",
				new EpsElegida("EPS Sura", null),
				new ContactoRegla("MARTA ROJAS", new Telefono("CO", "3201112233"), "Madre"),
				new SaludRegla(null, null, null, false), new Compromisos(true, true),
				new Consentimiento("2026-10-28T15:21:00Z", "TYC-2026-10-28", false), "Páramo");
	}

	static AcompananteRegla acompanante() {
		return new AcompananteRegla(persona(), false, null);
	}

	static NuevaReserva reserva() {
		return new NuevaReserva(titular(), "M", null, List.of(), null, "total", null);
	}

	static ContextoReserva contexto() {
		return new ContextoReserva(CATALOGOS, EVENTO, HOY, Set.of(), 60);
	}

	static NuevaReserva conTitular(NuevaReserva r, PersonaRegla titular) {
		return new NuevaReserva(titular, r.tallaTitular(), r.acompanante(), r.extras(), r.hotel(), r.plan(),
				r.fechaCompromiso());
	}

	static NuevaReserva conAcompanante(NuevaReserva r, AcompananteRegla a) {
		return new NuevaReserva(r.titular(), r.tallaTitular(), a, r.extras(), r.hotel(), r.plan(),
				r.fechaCompromiso());
	}

}
