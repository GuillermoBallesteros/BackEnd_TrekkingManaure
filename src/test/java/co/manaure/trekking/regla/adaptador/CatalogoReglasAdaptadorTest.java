package co.manaure.trekking.regla.adaptador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import co.manaure.trekking.catalogo.CatalogoService;
import co.manaure.trekking.catalogo.Estadia;
import co.manaure.trekking.catalogo.PaisTelefono;
import co.manaure.trekking.catalogo.TipoDocumento;
import co.manaure.trekking.regla.CatalogoReglas;

class CatalogoReglasAdaptadorTest {

	@Test
	void conviertePaisesYDocumentosDelCatalogoEnDatosPuros() {
		var servicio = mock(CatalogoService.class);

		var tipo = mock(TipoDocumento.class);
		when(tipo.getClave()).thenReturn("TI");
		when(tipo.getPatron()).thenReturn("[0-9]{10,11}");
		when(tipo.getAyuda()).thenReturn("10 u 11 dígitos");
		when(tipo.getEdadMin()).thenReturn((short) 7);
		when(tipo.getEdadMax()).thenReturn((short) 18);
		when(servicio.tiposDocumento()).thenReturn(List.of(tipo));

		var pais = mock(PaisTelefono.class);
		when(pais.getIso()).thenReturn("CO");
		when(pais.getNombre()).thenReturn("Colombia");
		when(pais.getIndicativo()).thenReturn("57");
		when(pais.getEjemplo()).thenReturn("300 123 4567");
		when(pais.getMovilLargos()).thenReturn(new Short[] { 10 });
		when(pais.getMovilInicios()).thenReturn(new String[] { "3" });
		when(pais.admiteFijo()).thenReturn(false);
		when(servicio.paises()).thenReturn(List.of(pais));

		var estadia = mock(Estadia.class);
		when(estadia.getClave()).thenReturn("Páramo");
		when(estadia.isOcupaCupo()).thenReturn(true);
		when(servicio.estadias()).thenReturn(List.of(estadia));

		when(servicio.generos()).thenReturn(List.of());
		when(servicio.tallas()).thenReturn(List.of());
		when(servicio.extras()).thenReturn(List.of());
		when(servicio.eps()).thenReturn(List.of());
		when(servicio.parentescos()).thenReturn(List.of());

		CatalogoReglas c = new CatalogoReglasAdaptador(servicio).catalogos();

		assertThat(c.documento("TI")).hasValueSatisfying(d -> {
			assertThat(d.edadMin()).isEqualTo(7);
			assertThat(d.edadMax()).isEqualTo(18);
			assertThat(d.patron()).isEqualTo("[0-9]{10,11}");
		});
		assertThat(c.pais("CO")).hasValueSatisfying(p -> {
			assertThat(p.movil().cumple("3001234567")).isTrue();
			assertThat(p.fijo()).isNull();
		});
		assertThat(c.ocupaCupo("Páramo")).isTrue();
	}

}
