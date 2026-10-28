package co.manaure.trekking.regla;

import co.manaure.trekking.regla.modelo.Compromisos;
import co.manaure.trekking.regla.modelo.Consentimiento;
import co.manaure.trekking.regla.modelo.ContactoRegla;
import co.manaure.trekking.regla.modelo.EpsElegida;
import co.manaure.trekking.regla.modelo.PersonaRegla;
import co.manaure.trekking.regla.modelo.SaludRegla;
import co.manaure.trekking.regla.modelo.Telefono;

/** Constructor de personas de prueba: parte de una persona válida y cambia un dato por vez. */
final class PB {

	String nombres, apellidos, tipoDocumento, documento, fechaNacimiento, genero, correo, ciudad, estadia;
	Telefono telefono;
	EpsElegida eps;
	ContactoRegla contacto;
	SaludRegla salud;
	Compromisos compromisos;
	Consentimiento consentimiento;

	private PB(PersonaRegla p) {
		nombres = p.nombres();
		apellidos = p.apellidos();
		tipoDocumento = p.tipoDocumento();
		documento = p.documento();
		fechaNacimiento = p.fechaNacimiento();
		genero = p.genero();
		telefono = p.telefono();
		correo = p.correo();
		ciudad = p.ciudad();
		eps = p.eps();
		contacto = p.contacto();
		salud = p.salud();
		compromisos = p.compromisos();
		consentimiento = p.consentimiento();
		estadia = p.estadia();
	}

	static PB titular() {
		return new PB(Datos.titular());
	}

	static PB acompanante() {
		return new PB(Datos.persona());
	}

	PB nombres(String v) { nombres = v; return this; }
	PB apellidos(String v) { apellidos = v; return this; }
	PB tipoDocumento(String v) { tipoDocumento = v; return this; }
	PB documento(String v) { documento = v; return this; }
	PB nacimiento(String v) { fechaNacimiento = v; return this; }
	PB genero(String v) { genero = v; return this; }
	PB telefono(String pais, String numero) { telefono = new Telefono(pais, numero); return this; }
	PB correo(String v) { correo = v; return this; }
	PB ciudad(String v) { ciudad = v; return this; }
	PB eps(String entidad, String otra) { eps = new EpsElegida(entidad, otra); return this; }
	PB contacto(String nombre, String pais, String numero, String parentesco) {
		contacto = new ContactoRegla(nombre, new Telefono(pais, numero), parentesco);
		return this;
	}
	PB salud(String enfermedad, String alergias, String discapacidad, Boolean ejercita) {
		salud = new SaludRegla(enfermedad, alergias, discapacidad, ejercita);
		return this;
	}
	PB compromisos(Boolean areas, Boolean ambiente) { compromisos = new Compromisos(areas, ambiente); return this; }
	PB consentimiento(String fecha, String version, Boolean imagen) {
		consentimiento = new Consentimiento(fecha, version, imagen);
		return this;
	}
	PB estadia(String v) { estadia = v; return this; }

	PersonaRegla build() {
		return new PersonaRegla(nombres, apellidos, tipoDocumento, documento, fechaNacimiento, genero, telefono,
				correo, ciudad, eps, contacto, salud, compromisos, consentimiento, estadia);
	}

}
