package co.manaure.trekking.persona;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FichaSaludRepository extends JpaRepository<FichaSalud, Long> {

	/** Carga por lotes para la lista del panel (HU-N07): una sola sentencia. Se descifra al leer. */
	@Query("select f from FichaSalud f where f.personaId in :ids")
	List<FichaSalud> porPersonas(@Param("ids") Collection<Long> ids);

}
