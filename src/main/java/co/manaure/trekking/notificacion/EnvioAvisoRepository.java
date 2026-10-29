package co.manaure.trekking.notificacion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvioAvisoRepository extends JpaRepository<EnvioAviso, Long> {

	/**
	 * Reclama el aviso: inserta la fila en PENDIENTE si no existe. Devuelve 1 si la reclamó y 0 si ya existía (la
	 * restricción única decide entre dos ejecuciones o dos instancias, sin abortar la transacción).
	 */
	@Modifying
	@Query(value = """
			INSERT INTO envio_aviso (inscripcion_id, tipo, referencia, estado)
			VALUES (:inscripcion, :tipo, :referencia, 'PENDIENTE')
			ON CONFLICT (inscripcion_id, tipo, referencia) DO NOTHING""", nativeQuery = true)
	int insertarSiNoExiste(@Param("inscripcion") long inscripcionId, @Param("tipo") String tipo,
			@Param("referencia") String referencia);

	/** Vuelve a reclamar un aviso FALLIDO con menos de {@code max} intentos. Devuelve 1 si lo reclamó. */
	@Modifying
	@Query(value = """
			UPDATE envio_aviso SET estado = 'PENDIENTE', intentos = intentos + 1, actualizado_en = now()
			WHERE inscripcion_id = :inscripcion AND tipo = :tipo AND referencia = :referencia
			  AND estado = 'FALLIDO' AND intentos < :max""", nativeQuery = true)
	int reintentarSiFallo(@Param("inscripcion") long inscripcionId, @Param("tipo") String tipo,
			@Param("referencia") String referencia, @Param("max") int max);

	Optional<EnvioAviso> findByInscripcionIdAndTipoAndReferencia(Long inscripcionId, TipoAviso tipo,
			String referencia);

	@Modifying
	@Query(value = """
			UPDATE envio_aviso SET estado = :estado, mensaje_id = :mensaje, actualizado_en = now()
			WHERE id = :id""", nativeQuery = true)
	int marcar(@Param("id") long id, @Param("estado") String estado, @Param("mensaje") String mensajeId);

}
