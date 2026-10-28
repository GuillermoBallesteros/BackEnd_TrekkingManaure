package co.manaure.trekking.administrador;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AdministradorRepository extends JpaRepository<Administrador, Long> {

	Optional<Administrador> findByCorreo(String correo);

	/** Bloquea la fila para que los intentos de una misma cuenta se cuenten en serie. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Administrador> findWithLockByCorreo(String correo);

}
