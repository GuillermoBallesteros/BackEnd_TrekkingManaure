package co.manaure.trekking.cifrado;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra con AES-256-GCM los detalles de la ficha de salud (diseno §10.4). Valor guardado:
 * {@code <version>:<base64(iv || texto cifrado || etiqueta)>} con IV aleatorio de 96 bits por valor y etiqueta
 * de 128 bits. Los nulos no se cifran. Se aplica solo donde se declara con {@code @Convert}. Los errores no
 * incluyen el texto ni el valor cifrado.
 */
@Component
@Converter(autoApply = false)
public class CifradoSaludConverter implements AttributeConverter<String, String> {

	private static final int BYTES_IV = 12;
	private static final int BITS_ETIQUETA = 128;

	private final ClaveCifrado claves;
	private final SecureRandom aleatorio = new SecureRandom();

	public CifradoSaludConverter(ClaveCifrado claves) {
		this.claves = claves;
	}

	@Override
	public String convertToDatabaseColumn(String texto) {
		if (texto == null) {
			return null;
		}
		try {
			byte[] iv = new byte[BYTES_IV];
			aleatorio.nextBytes(iv);
			Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
			cifrador.init(Cipher.ENCRYPT_MODE, claves.claveActual(), new GCMParameterSpec(BITS_ETIQUETA, iv));
			byte[] cifrado = cifrador.doFinal(texto.getBytes(StandardCharsets.UTF_8));
			byte[] salida = ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array();
			return claves.versionActual() + ":" + Base64.getEncoder().encodeToString(salida);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("No se pudo cifrar un dato de salud.");
		}
	}

	@Override
	public String convertToEntityAttribute(String guardado) {
		if (guardado == null) {
			return null;
		}
		int separador = guardado.indexOf(':');
		if (separador <= 0) {
			throw new IllegalStateException("Dato de salud cifrado con formato desconocido.");
		}
		SecretKey clave;
		byte[] datos;
		try {
			clave = claves.clave(Integer.parseInt(guardado.substring(0, separador)));
			datos = Base64.getDecoder().decode(guardado.substring(separador + 1));
		} catch (IllegalArgumentException ex) {
			throw new IllegalStateException("Dato de salud cifrado con formato desconocido.");
		}
		if (clave == null) {
			throw new IllegalStateException("No hay clave configurada para la versión del dato de salud cifrado.");
		}
		if (datos.length <= BYTES_IV) {
			throw new IllegalStateException("Dato de salud cifrado con formato desconocido.");
		}
		try {
			Cipher cifrador = Cipher.getInstance("AES/GCM/NoPadding");
			cifrador.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, datos, 0, BYTES_IV));
			return new String(cifrador.doFinal(datos, BYTES_IV, datos.length - BYTES_IV), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("No se pudo descifrar un dato de salud.");
		}
	}

}
