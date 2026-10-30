package co.manaure.trekking.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Base de HU-N11 y HU-N12: restaura la configuración editable y borra la auditoría de configuración. */
abstract class ConfiguracionAdminBase extends ReservaPublicaBase {

	@BeforeEach
	void restaurarAntes() {
		restaurar();
	}

	@AfterEach
	void restaurarDespues() {
		restaurar();
	}

	private void restaurar() {
		jdbc.update("delete from auditoria where entidad = 'CONFIGURACION'");
		jdbc.update("""
				update configuracion_evento set cupos_carrera = 60, cupos_paramo = 60, fecha_cierre = date '2026-11-09',
				  actualizado_por = null, actualizado_en = null""");
	}

	protected ResultActions enviarPut(String ruta, String cuerpoJson) throws Exception {
		return mvc.perform(put(ruta).header("Authorization", "Bearer " + tokenAdmin())
				.contentType(MediaType.APPLICATION_JSON).content(cuerpoJson));
	}

	protected ResultActions putSinToken(String ruta, String cuerpoJson) throws Exception {
		return mvc.perform(put(ruta).contentType(MediaType.APPLICATION_JSON).content(cuerpoJson));
	}

	/**
	 * Deja {@code parejas} reservas activas (cupos de la carrera) y {@code parejas * 2} cupos del páramo ocupados:
	 * reservas con acompañante en el páramo y pago aprobado.
	 */
	protected void ocupar(int parejas) throws Exception {
		for (int i = 0; i < parejas; i++) {
			enviar(ReservaJson.conAcompanante().set("document", "106500010" + i).set("email", "t" + i + "@example.com")
					.set("companion.document", "106599980" + i).set("companion.email", "c" + i + "@example.com"))
					.andExpect(status().isCreated());
			aprobarPagos(String.format("PER-%04d", i + 1));
		}
	}

}
