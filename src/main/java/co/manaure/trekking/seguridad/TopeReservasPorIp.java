package co.manaure.trekking.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import co.manaure.trekking.auditoria.AccionAuditoria;
import co.manaure.trekking.auditoria.AuditoriaService;
import co.manaure.trekking.auditoria.EntidadAuditoria;

/**
 * Tope diario por IP para {@code POST /api/v1/reservas} (H4): cuenta todas las solicitudes a esa ruta, también las
 * que fallan por validación o por el límite por minuto. Ventana fija de 24 horas que empieza con la primera
 * solicitud de la IP, en memoria. Al pasar el tope rechaza (el filtro responde 429 con Retry-After) y dispara UNA
 * alerta por IP y ventana: una línea WARN y una fila de auditoría ALERTA_RESERVAS_IP. Ni el log ni la auditoría
 * llevan datos personales: la IP aparece solo como hash corto (las IP no se registran en otros logs del proyecto).
 * Las entradas vencidas se limpian como máximo cada {@link #INTERVALO_LIMPIEZA_MS}, no en cada solicitud.
 */
@Component
public class TopeReservasPorIp {

	private static final Logger log = LoggerFactory.getLogger(TopeReservasPorIp.class);
	static final long VENTANA_MS = 24L * 60 * 60 * 1000;
	static final long INTERVALO_LIMPIEZA_MS = 10L * 60 * 1000;

	/** {@code alertaNueva} solo es true en el valor devuelto por la solicitud que cruzó el tope. */
	private record Ventana(long finMs, int cuenta, boolean alertada, boolean alertaNueva) {
	}

	/** Resultado de contar una solicitud: si se rechaza y cuántos segundos faltan para el fin de la ventana. */
	public record Decision(boolean rechazada, long retryAfterSegundos) {
	}

	private final Clock clock;
	private final AuditoriaService auditoria;
	private final int limite;
	private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
	private final AtomicLong proximaLimpiezaMs = new AtomicLong();

	public TopeReservasPorIp(Clock clock, AuditoriaService auditoria,
			@Value("${app.rate-limit.reservas-por-ip-por-dia:20}") int limite) {
		this.clock = clock;
		this.auditoria = auditoria;
		this.limite = limite;
	}

	/** Cuenta la solicitud de esta IP y decide. */
	public Decision registrar(String ip) {
		long ahora = clock.millis();
		limpiarSiToca(ahora);
		Ventana v = ventanas.compute(ip, (k, actual) -> {
			if (actual == null || actual.finMs() <= ahora) {
				return new Ventana(ahora + VENTANA_MS, 1, false, false);
			}
			int cuenta = Math.min(actual.cuenta() + 1, limite + 1);
			boolean cruza = cuenta > limite && !actual.alertada();
			return new Ventana(actual.finMs(), cuenta, actual.alertada() || cruza, cruza);
		});
		if (v.cuenta() <= limite) {
			return new Decision(false, 0);
		}
		if (v.alertaNueva()) {
			alertar(ip);
		}
		return new Decision(true, Math.max(1, (long) Math.ceil((v.finMs() - ahora) / 1000.0)));
	}

	private void alertar(String ip) {
		String ref = hashCorto(ip);
		log.warn("ALERTA: la IP {} superó {} solicitudes de reserva en 24 horas", ref, limite);
		try {
			auditoria.registrar(null, AccionAuditoria.ALERTA_RESERVAS_IP, EntidadAuditoria.RESERVA, "IP-" + ref,
					"limite=" + limite + "/24h");
		}
		catch (RuntimeException ex) {
			// La alerta no debe impedir el 429; el log ya quedó escrito.
			log.error("No se pudo auditar la alerta de reservas por IP ({}).", ex.getClass().getSimpleName());
		}
	}

	/** Elimina las entradas vencidas; un solo hilo a la vez y a lo sumo cada 10 minutos. */
	private void limpiarSiToca(long ahora) {
		long toca = proximaLimpiezaMs.get();
		if (ahora >= toca && proximaLimpiezaMs.compareAndSet(toca, ahora + INTERVALO_LIMPIEZA_MS)) {
			ventanas.values().removeIf(v -> v.finMs() <= ahora);
		}
	}

	/** Primeros 8 caracteres hexadecimales de SHA-256 de la IP. */
	static String hashCorto(String ip) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(h, 0, 4);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	int tamano() {
		return ventanas.size();
	}

}
