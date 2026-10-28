package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EpsRepository extends JpaRepository<Eps, String> {

	List<Eps> findAllByOrderByOrdenAsc();

}
