package co.manaure.trekking.reserva;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InscripcionExtraRepository extends JpaRepository<InscripcionExtra, InscripcionExtraId> {

	/** Carga por lotes para la lista del panel (HU-N07), en el orden del catálogo de extras. */
	@Query("""
			select e from InscripcionExtra e join Extra x on x.clave = e.id.extra
			where e.id.inscripcionId in :ids order by x.orden asc""")
	List<InscripcionExtra> porInscripciones(@Param("ids") Collection<Long> ids);

}
