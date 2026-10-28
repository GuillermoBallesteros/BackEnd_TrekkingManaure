package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EstadiaRepository extends JpaRepository<Estadia, String> {

	List<Estadia> findAllByOrderByOrdenAsc();

}
