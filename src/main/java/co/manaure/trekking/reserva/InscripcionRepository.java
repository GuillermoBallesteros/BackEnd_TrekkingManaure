package co.manaure.trekking.reserva;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

	Optional<Inscripcion> findByCodigo(String codigo);

	/** Todas las inscripciones, activas o no, la más reciente primero (HU-N07). */
	List<Inscripcion> findAllByOrderByIdDesc();

	/**
	 * Candidatas a caducar (sin bloqueo; se vuelve a comprobar bajo bloqueo): reservas con algún pago "Por revisar"
	 * en revisión desde {@code limite} o antes y ningún pago aprobado. Paginadas por id ascendente.
	 */
	@Query("""
			select i from Inscripcion i where i.id > :desde
			and exists (select 1 from Pago p where p.inscripcion = i and p.revision = co.manaure.trekking.pago.RevisionPago.POR_REVISAR
			            and p.enRevisionDesde <= :limite)
			and not exists (select 1 from Pago a where a.inscripcion = i and a.revision = co.manaure.trekking.pago.RevisionPago.APROBADO)
			order by i.id""")
	List<Inscripcion> candidatasACaducar(@Param("desde") long desde, @Param("limite") java.time.OffsetDateTime limite,
			org.springframework.data.domain.Pageable lote);

	/** FOR UPDATE de la fila de la inscripción (diseno §9); se toma después del bloqueo global. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select i from Inscripcion i where i.codigo = :codigo")
	Optional<Inscripcion> findByCodigoForUpdate(@Param("codigo") String codigo);

}
