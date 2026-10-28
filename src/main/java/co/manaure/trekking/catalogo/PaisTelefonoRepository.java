package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaisTelefonoRepository extends JpaRepository<PaisTelefono, String> {

	List<PaisTelefono> findAllByOrderByOrdenAsc();

}
