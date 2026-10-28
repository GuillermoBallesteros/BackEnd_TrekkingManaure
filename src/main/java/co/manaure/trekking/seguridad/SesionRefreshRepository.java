package co.manaure.trekking.seguridad;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface SesionRefreshRepository extends JpaRepository<SesionRefresh, Long> {

	Optional<SesionRefresh> findByTokenHash(String tokenHash);

	/** Bloquea la fila: dos refresh simultáneos con el mismo token se atienden en serie. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<SesionRefresh> findWithLockByTokenHash(String tokenHash);

	/** Inicio de la familia (el primer refresh): {@code iniciadaEn} no cambia al rotar. */
	@Query("select min(s.creadoEn) from SesionRefresh s where s.familia = :familia")
	Instant findInicioDeFamilia(@Param("familia") UUID familia);

	/** Revoca todos los refresh de la familia que aún no lo estén. Devuelve cuántos revocó. */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update SesionRefresh s set s.revocadoEn = :ahora where s.familia = :familia and s.revocadoEn is null")
	int revocarFamilia(@Param("familia") UUID familia, @Param("ahora") Instant ahora);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from SesionRefresh s where s.expiraEn < :limite")
	int borrarVencidosAntesDe(@Param("limite") Instant limite);

}
