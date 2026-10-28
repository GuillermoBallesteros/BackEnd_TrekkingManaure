package co.manaure.trekking.configuracion;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ConfiguracionRepository extends JpaRepository<ConfiguracionEvento, Short> {

	/**
	 * Bloqueo global (diseno §9): SELECT ... FOR UPDATE sobre la fila única. Serializa las comprobaciones de
	 * cupo y de documento duplicado. Debe llamarse dentro de una transacción; el tiempo de espera lo fija
	 * jakarta.persistence.lock.timeout.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from ConfiguracionEvento c where c.id = 1")
	Optional<ConfiguracionEvento> bloquear();

}
