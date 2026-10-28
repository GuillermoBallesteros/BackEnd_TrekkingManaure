package co.manaure.trekking.api;

import java.io.IOException;
import java.io.InputStream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Payload de {@code NewRegistration} armado desde fixtures JSON con la misma forma que genera
 * {@code buildNewRegistration} del front, con modificaciones puntuales por ruta ({@code companion.phone.number}).
 */
final class ReservaJson {

	static final JsonMapper MAPPER = JsonMapper.builder().build();

	private final ObjectNode raiz;

	private ReservaJson(ObjectNode raiz) {
		this.raiz = raiz;
	}

	private static ObjectNode cargar(String recurso) {
		try (InputStream in = ReservaJson.class.getResourceAsStream("/fixtures/" + recurso)) {
			return (ObjectNode) MAPPER.readTree(in);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Titular solo, plan total, estadía en el páramo. */
	static ReservaJson titular() {
		return new ReservaJson(cargar("nueva-reserva.json"));
	}

	/** Titular más acompañante sin kit (el bloque {@code companion} sale del fixture del acompañante). */
	static ReservaJson conAcompanante() {
		ReservaJson r = titular();
		r.raiz.set("companion", cargar("acompanante.json"));
		return r;
	}

	ReservaJson set(String ruta, Object valor) {
		String[] partes = ruta.split("\\.");
		ObjectNode nodo = raiz;
		for (int i = 0; i < partes.length - 1; i++) {
			nodo = (ObjectNode) nodo.get(partes[i]);
		}
		String hoja = partes[partes.length - 1];
		if (valor == null) {
			nodo.putNull(hoja);
		} else {
			nodo.set(hoja, valor instanceof JsonNode n ? n : MAPPER.valueToTree(valor));
		}
		return this;
	}

	ReservaJson quitar(String ruta) {
		String[] partes = ruta.split("\\.");
		ObjectNode nodo = raiz;
		for (int i = 0; i < partes.length - 1; i++) {
			nodo = (ObjectNode) nodo.get(partes[i]);
		}
		nodo.remove(partes[partes.length - 1]);
		return this;
	}

	String json() {
		return MAPPER.writeValueAsString(raiz);
	}

}
