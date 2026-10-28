package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TallaRepository extends JpaRepository<Talla, String> {

	List<Talla> findAllByOrderByOrdenAsc();

}
