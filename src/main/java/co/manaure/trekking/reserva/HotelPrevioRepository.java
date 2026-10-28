package co.manaure.trekking.reserva;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HotelPrevioRepository extends JpaRepository<HotelPrevio, Long> {

	/** Carga por lotes para la lista del panel (HU-N07). */
	@Query("select h from HotelPrevio h where h.inscripcionId in :ids")
	List<HotelPrevio> porInscripciones(@Param("ids") Collection<Long> ids);

}
