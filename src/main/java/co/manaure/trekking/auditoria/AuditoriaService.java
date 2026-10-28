package co.manaure.trekking.auditoria;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra acciones auditables. {@code detalle} nunca lleva claves, correos ni datos personales. */
@Service
public class AuditoriaService {

	private final AuditoriaRepository repositorio;

	public AuditoriaService(AuditoriaRepository repositorio) {
		this.repositorio = repositorio;
	}

	@Transactional
	public void registrar(Long administradorId, AccionAuditoria accion, EntidadAuditoria entidad, String entidadRef,
			String detalle) {
		repositorio.save(new Auditoria(administradorId, accion, entidad, entidadRef, detalle));
	}

}
