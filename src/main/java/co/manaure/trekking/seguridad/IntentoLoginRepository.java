package co.manaure.trekking.seguridad;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

public interface IntentoLoginRepository extends JpaRepository<IntentoLogin, Long> {

	Optional<IntentoLogin> findByIpAndCorreoHash(String ip, String correoHash);

	/** Bloquea la fila de la pareja para contar en serie los fallos concurrentes. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<IntentoLogin> findWithLockByIpAndCorreoHash(String ip, String correoHash);

	/** Crea la fila de la pareja si no existe (atómico; sin error si otra transacción la creó). */
	@Modifying(flushAutomatically = true)
	@Query(value = "INSERT INTO intento_login (ip, correo_hash, fallos, ultimo_fallo_en) "
			+ "VALUES (:ip, :correoHash, 0, :ahora) ON CONFLICT (ip, correo_hash) DO NOTHING", nativeQuery = true)
	int asegurar(@Param("ip") String ip, @Param("correoHash") String correoHash, @Param("ahora") Instant ahora);

	@Modifying(flushAutomatically = true)
	@Query("delete from IntentoLogin i where i.ip = :ip and i.correoHash = :correoHash")
	int reiniciar(@Param("ip") String ip, @Param("correoHash") String correoHash);

	/** Fallos de una cuenta sumados de todas las IP desde el instante dado. */
	@Query("select coalesce(sum(i.fallos), 0) from IntentoLogin i where i.correoHash = :correoHash and i.ultimoFalloEn >= :desde")
	long fallosDesde(@Param("correoHash") String correoHash, @Param("desde") Instant desde);

	@Transactional
	@Modifying
	@Query("delete from IntentoLogin i where i.ultimoFalloEn < :limite and (i.bloqueadoHasta is null or i.bloqueadoHasta < :limite)")
	int borrarAntesDe(@Param("limite") Instant limite);

}
