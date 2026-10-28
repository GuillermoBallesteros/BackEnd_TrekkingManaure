package co.manaure.trekking.catalogo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import co.manaure.trekking.TestcontainersConfiguration;

/** HU-N01: los catálogos se leen de la base como entidades de solo lectura, en orden y con caché simple. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class CatalogoServiceTest {

	@Autowired
	CatalogoService catalogos;

	@Test
	void devuelveLosCatalogosCompletosEnElOrdenDelFront() {
		assertThat(catalogos.tiposDocumento()).extracting(TipoDocumento::getClave)
				.containsExactly("CC", "CE", "TI", "RC", "Pasaporte", "PPT");
		assertThat(catalogos.generos()).hasSize(4);
		assertThat(catalogos.tallas()).extracting(Talla::getClave).containsExactly("XS", "S", "M", "L", "XL", "XXL", "XXXL");
		assertThat(catalogos.estadias()).extracting(Estadia::getClave)
				.containsExactly("Páramo", "Hotel en Manaure", "Regreso a casa");
		assertThat(catalogos.extras()).hasSize(3);
		assertThat(catalogos.eps()).hasSize(25);
		assertThat(catalogos.parentescos()).hasSize(11);
		assertThat(catalogos.paises()).hasSize(13);
	}

	@Test
	void exponeLasBanderasDeCadaCatalogo() {
		assertThat(catalogos.estadia("Páramo")).get().extracting(Estadia::isOcupaCupo).isEqualTo(true);
		assertThat(catalogos.estadia("Regreso a casa")).get().extracting(Estadia::isOcupaCupo).isEqualTo(false);
		assertThat(catalogos.extra("parapenteAcompanante")).get().extracting(Extra::isRequiereAcompanante).isEqualTo(true);
		assertThat(catalogos.eps("Otra")).get().extracting(Eps::isPideNombre).isEqualTo(true);
		assertThat(catalogos.eps("Nueva EPS")).get().extracting(Eps::isPideNombre).isEqualTo(false);
		assertThat(catalogos.parentesco("Madre")).get().extracting(Parentesco::isEsResponsableLegal).isEqualTo(true);
		assertThat(catalogos.parentesco("Amigo o amiga")).get().extracting(Parentesco::isEsResponsableLegal)
				.isEqualTo(false);
		TipoDocumento ti = catalogos.tipoDocumento("TI").orElseThrow();
		assertThat(ti.getEdadMin()).isEqualTo((short) 7);
		assertThat(ti.getEdadMax()).isEqualTo((short) 18);
		assertThat(catalogos.tipoDocumento("XX")).isEmpty();
	}

	@Test
	void paisesTraenLasReglasDeMovilYFijo() {
		PaisTelefono co = catalogos.pais("CO").orElseThrow();
		assertThat(co.getIndicativo()).isEqualTo("57");
		assertThat(co.getMovilLargos()).containsExactly((short) 10);
		assertThat(co.getMovilInicios()).containsExactly("3");
		assertThat(co.getFijoInicios()).containsExactly("60");
		PaisTelefono ar = catalogos.pais("AR").orElseThrow();
		assertThat(ar.getMovilLargos()).containsExactly((short) 10, (short) 11);
		assertThat(ar.admiteFijo()).isFalse();
		assertThat(catalogos.pais("DO").orElseThrow().getMovilInicios()).containsExactly("809", "829", "849");
	}

	@Test
	void laCacheDevuelveLosMismosDatosYSePuedeRecargar() {
		assertThat(catalogos.eps("Otra")).isPresent();
		catalogos.recargar();
		assertThat(catalogos.eps()).hasSize(25);
	}

}
