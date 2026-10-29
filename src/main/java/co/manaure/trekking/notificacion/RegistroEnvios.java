package co.manaure.trekking.notificacion;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reclamo y cierre de cada aviso en {@code envio_aviso}, cada paso en su propia transacción: se reclama antes de
 * llamar a Brevo y se marca después, fuera de cualquier transacción abierta. Si la API cae entre el reclamo y la
 * marca, la fila queda PENDIENTE y ese aviso no se repite: como máximo una vez.
 */
@Service
public class RegistroEnvios {

	static final int MAX_INTENTOS = 3;

	private final EnvioAvisoRepository envios;

	public RegistroEnvios(EnvioAvisoRepository envios) {
		this.envios = envios;
	}

	/** @return el id de la fila reclamada, o vacío si el aviso ya se envió, está en curso o agotó sus intentos */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Optional<Long> reclamar(long inscripcionId, Aviso aviso) {
		String tipo = aviso.tipo().name();
		boolean reclamado = envios.insertarSiNoExiste(inscripcionId, tipo, aviso.referencia()) == 1
				|| envios.reintentarSiFallo(inscripcionId, tipo, aviso.referencia(), MAX_INTENTOS) == 1;
		if (!reclamado) {
			return Optional.empty();
		}
		return envios.findByInscripcionIdAndTipoAndReferencia(inscripcionId, aviso.tipo(), aviso.referencia())
				.map(EnvioAviso::getId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void marcarEnviado(long id, String mensajeId) {
		envios.marcar(id, EstadoEnvio.ENVIADO.name(), mensajeId);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void marcarFallido(long id) {
		envios.marcar(id, EstadoEnvio.FALLIDO.name(), null);
	}

}
