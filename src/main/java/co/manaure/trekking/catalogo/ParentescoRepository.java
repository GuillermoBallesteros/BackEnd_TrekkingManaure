package co.manaure.trekking.catalogo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParentescoRepository extends JpaRepository<Parentesco, String> {

	List<Parentesco> findAllByOrderByOrdenAsc();

}
