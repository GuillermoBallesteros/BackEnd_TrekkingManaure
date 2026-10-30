package co.manaure.trekking.reserva;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InscripcionParticipanteRepository extends JpaRepository<InscripcionParticipante, Long> {

	/** Carga por lotes para la lista del panel (HU-N07): participantes con su persona, una sola sentencia. */
	@Query("select ip from InscripcionParticipante ip join fetch ip.persona where ip.inscripcion.id in :ids")
	List<InscripcionParticipante> porInscripciones(@Param("ids") Collection<Long> ids);

	/**
	 * Cupos del páramo ocupados (RN-04): uno por persona, titular o acompañante, con estadía que ocupa cupo en una
	 * inscripción activa. El cupo se aparta al crear la reserva y se libera cuando deja de estar activa (rechazada o
	 * caducada). Una sola consulta agregada. {@code excluir} = -1 si no se excluye ninguna inscripción.
	 */
	@Query(value = """
			SELECT count(*) FROM inscripcion_participante ip
			JOIN inscripcion i ON i.id = ip.inscripcion_id AND i.activa
			JOIN estadia e ON e.clave = ip.estadia AND e.ocupa_cupo
			WHERE ip.inscripcion_id <> :excluir
			""", nativeQuery = true)
	long contarOcupados(@Param("excluir") long excluir);

	/**
	 * Documentos de la lista retenidos por OTRA inscripción activa (RN-06, RN-18), según la columna
	 * {@code documento_activo} que respalda el índice único.
	 */
	@Query(value = """
			SELECT documento_activo FROM inscripcion_participante
			WHERE documento_activo IN (:documentos) AND inscripcion_id <> :excluir
			""", nativeQuery = true)
	List<String> documentosTomadosPorOtras(@Param("documentos") Collection<String> documentos,
			@Param("excluir") long excluir);

	/**
	 * Documentos (en mayúsculas) de la lista que ya tienen una inscripción activa (RN-06): estado derivado distinto
	 * de Rechazado. Rechazada = tiene pagos y todos están rechazados; sin pagos o con alguno por revisar o
	 * aprobado, está activa.
	 */
	@Query(value = """
			SELECT pe.documento FROM inscripcion_participante ip
			JOIN persona pe ON pe.id = ip.persona_id
			WHERE pe.documento IN (:documentos)
			  AND NOT (EXISTS (SELECT 1 FROM pago x WHERE x.inscripcion_id = ip.inscripcion_id)
			           AND NOT EXISTS (SELECT 1 FROM pago x WHERE x.inscripcion_id = ip.inscripcion_id
			                           AND x.revision <> 'RECHAZADO'))
			""", nativeQuery = true)
	List<String> documentosActivos(@Param("documentos") Collection<String> documentos);

}
