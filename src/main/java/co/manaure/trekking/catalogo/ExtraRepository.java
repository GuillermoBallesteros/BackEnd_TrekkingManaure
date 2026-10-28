package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtraRepository extends JpaRepository<Extra, String> {

	List<Extra> findAllByOrderByOrdenAsc();

}
