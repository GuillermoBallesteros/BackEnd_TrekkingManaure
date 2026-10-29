package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.manaure.trekking.notificacion.ClienteBrevo;
import co.manaure.trekking.notificacion.TipoAviso;

/** HU-N15: un tipo sin plantilla configurada se omite sin reclamarlo; los demás siguen enviándose. */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = { "app.brevo.activo=true", "app.brevo.api-key=clave-prueba",
		"app.brevo.plantillas.pago-aprobado=5" })
class AvisosSinPlantillaTest extends ReservaPublicaBase {

	@MockitoBean
	ClienteBrevo cliente;

	@Test
	void unTipoSinPlantillaSeOmiteConUnaAdvertenciaSinDatosPersonales(CapturedOutput salida) throws Exception {
		when(cliente.enviar(any(), anyLong(), anyMap(), any())).thenReturn("<id@brevo>");
		enviar(ReservaJson.titular()).andExpect(status().isCreated());

		revisar("PAG-0001", "Rechazado", "El monto recibido no coincide con lo esperado").andExpect(status().isOk());
		// El aviso sale en el hilo de avisos: se le da tiempo antes de comprobar que no envió nada.
		verify(cliente, after(500).never()).enviar(any(), anyLong(), anyMap(), any());
		assertThat(jdbc.queryForObject("select count(*) from envio_aviso", Integer.class)).isZero();
		assertThat(salida.getAll()).contains("Avisos: PAGO_RECHAZADO no tiene plantilla configurada; se omite.")
				.doesNotContain("ana@example.com");

		revisar("PAG-0001", "Aprobado", null).andExpect(status().isOk());
		verify(cliente, timeout(5000).times(1)).enviar(any(), eq(5L), anyMap(), eq(TipoAviso.PAGO_APROBADO));
	}

}
