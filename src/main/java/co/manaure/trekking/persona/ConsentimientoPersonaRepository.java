package co.manaure.trekking.persona;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsentimientoPersonaRepository extends JpaRepository<ConsentimientoPersona, Long> {

	/** Carga por lotes para la lista del panel (HU-N07). */
	@Query("select c from ConsentimientoPersona c where c.personaId in :ids")
	List<ConsentimientoPersona> porPersonas(@Param("ids") Collection<Long> ids);

}
