package co.manaure.trekking.cifrado;

import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Claves AES-256 con versión para el cifrado de la ficha de salud (diseno §10.4). La clave actual
 * (HEALTH_ENCRYPTION_KEY, {@code version:base64} de 32 bytes) cifra; las anteriores
 * (HEALTH_ENCRYPTION_KEY_ANTERIORES, separadas por coma) solo descifran. Si falta o es inválida, la
 * construcción falla y la aplicación no arranca. Los mensajes de error nunca incluyen la clave.
 */
@Component
public class ClaveCifrado {

	static final int BYTES_CLAVE = 32;

	private final int versionActual;
	private final Map<Integer, SecretKey> claves = new HashMap<>();

	public ClaveCifrado(@Value("${app.cifrado.salud-clave:}") String actual,
			@Value("${app.cifrado.salud-claves-anteriores:}") String anteriores) {
		if (actual == null || actual.isBlank()) {
			throw new IllegalStateException("HEALTH_ENCRYPTION_KEY es obligatoria (formato version:base64 de 32 bytes).");
		}
		Entrada principal = interpretar(actual.trim(), "HEALTH_ENCRYPTION_KEY");
		this.versionActual = principal.version();
		this.claves.put(principal.version(), principal.clave());
		if (anteriores != null && !anteriores.isBlank()) {
			for (String texto : anteriores.split(",")) {
				if (texto.isBlank()) {
					continue;
				}
				Entrada anterior = interpretar(texto.trim(), "HEALTH_ENCRYPTION_KEY_ANTERIORES");
				if (claves.putIfAbsent(anterior.version(), anterior.clave()) != null) {
					throw new IllegalStateException(
							"HEALTH_ENCRYPTION_KEY_ANTERIORES repite una versión de clave ya definida.");
				}
			}
		}
	}

	private record Entrada(int version, SecretKey clave) {
	}

	private static Entrada interpretar(String texto, String variable) {
		int separador = texto.indexOf(':');
		if (separador <= 0 || separador == texto.length() - 1) {
			throw new IllegalStateException(variable + " debe tener el formato version:base64.");
		}
		String versionTexto = texto.substring(0, separador);
		if (!versionTexto.matches("[0-9]{1,6}") || Integer.parseInt(versionTexto) < 1) {
			throw new IllegalStateException(variable + ": la versión debe ser un entero positivo.");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(texto.substring(separador + 1));
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException(variable + ": la clave no es base64 válido.");
		}
		if (bytes.length != BYTES_CLAVE) {
			Arrays.fill(bytes, (byte) 0);
			throw new IllegalStateException(variable + ": la clave debe decodificar a " + BYTES_CLAVE + " bytes.");
		}
		SecretKey clave = new SecretKeySpec(bytes, "AES");
		Arrays.fill(bytes, (byte) 0);
		return new Entrada(Integer.parseInt(versionTexto), clave);
	}

	public int versionActual() {
		return versionActual;
	}

	public SecretKey claveActual() {
		return claves.get(versionActual);
	}

	/** Clave de una versión, o null si no está configurada. */
	public SecretKey clave(int version) {
		return claves.get(version);
	}

}
