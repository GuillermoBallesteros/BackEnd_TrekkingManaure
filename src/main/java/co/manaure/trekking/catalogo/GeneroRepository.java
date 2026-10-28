package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneroRepository extends JpaRepository<Genero, String> {

	List<Genero> findAllByOrderByOrdenAsc();

}
