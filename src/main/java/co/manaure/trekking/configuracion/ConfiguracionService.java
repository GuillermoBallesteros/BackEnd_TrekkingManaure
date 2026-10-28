package co.manaure.trekking.configuracion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfiguracionService {

	private final ConfiguracionRepository repositorio;

	public ConfiguracionService(ConfiguracionRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional(readOnly = true)
	public ConfiguracionEvento obtener() {
		return repositorio.findById(ConfiguracionEvento.ID).orElseThrow(ConfiguracionService::falta);
	}

	/**
	 * Toma el bloqueo global de escritura (diseno §9). Exige una transacción en curso: el bloqueo dura hasta
	 * que ella termine, así que lo piden los servicios que crean reservas o cambian pagos y cupos.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public ConfiguracionEvento obtenerConBloqueo() {
		return repositorio.bloquear().orElseThrow(ConfiguracionService::falta);
	}

	private static IllegalStateException falta() {
		return new IllegalStateException("Falta la fila de configuracion_evento (id = 1).");
	}

}
