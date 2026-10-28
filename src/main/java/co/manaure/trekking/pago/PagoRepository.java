package co.manaure.trekking.pago;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	Optional<Pago> findByCodigo(String codigo);

	/** Código de la inscripción de un pago, sin cargar el pago (se recarga después de tomar los bloqueos). */
	@Query("select p.inscripcion.codigo from Pago p where p.codigo = :codigo")
	Optional<String> codigoInscripcionDe(@Param("codigo") String codigo);

	List<Pago> findByInscripcionIdOrderByIdAsc(Long inscripcionId);

	/** Carga por lotes para la lista del panel (HU-N07): pagos de varias inscripciones, por id ascendente. */
	@Query("select p from Pago p where p.inscripcion.id in :ids order by p.id asc")
	List<Pago> porInscripciones(@Param("ids") Collection<Long> ids);

}
