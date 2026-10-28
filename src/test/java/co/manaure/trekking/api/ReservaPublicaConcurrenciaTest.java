package co.manaure.trekking.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** HU-N04: solicitudes simultáneas contra el bloqueo global de configuración_evento (diseño §9). */
class ReservaPublicaConcurrenciaTest extends ReservaPublicaBase {

	/** Lanza las solicitudes a la vez y devuelve los estados HTTP en el mismo orden. */
	private List<Integer> enviarEnParalelo(List<String> cuerpos) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(cuerpos.size());
		CountDownLatch salida = new CountDownLatch(1);
		try {
			List<Future<Integer>> futuros = new ArrayList<>();
			for (String cuerpo : cuerpos) {
				futuros.add(pool.submit(() -> {
					salida.await();
					return enviar(cuerpo).andReturn().getResponse().getStatus();
				}));
			}
			salida.countDown();
			List<Integer> estados = new ArrayList<>();
			for (Future<Integer> f : futuros) {
				estados.add(f.get(60, TimeUnit.SECONDS));
			}
			return estados;
		} finally {
			pool.shutdownNow();
		}
	}

	@RepeatedTest(5)
	void mismoDocumentoSimultaneoDa201Y409() throws Exception {
		String cuerpo = ReservaJson.titular().json();

		List<Integer> estados = enviarEnParalelo(List.of(cuerpo, cuerpo));

		assertThat(estados).containsExactlyInAnyOrder(201, 409);
		assertThat(contar("inscripcion")).isEqualTo(1);
		assertThat(contar("pago")).isEqualTo(1);
	}

	@Test
	void documentosDistintosPorElUltimoCupoResponden201LosDos() throws Exception {
		jdbc.update("update configuracion_evento set cupos_paramo = 1");

		List<Integer> estados = enviarEnParalelo(List.of(ReservaJson.titular().json(),
				ReservaJson.titular().set("document", "1065000111").set("email", "otra@example.com").json()));

		// Reservar no aparta cupo (RN-04): el cupo se asigna al aprobar el pago.
		assertThat(estados).containsExactly(201, 201);
		assertThat(contar("inscripcion")).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from pago where revision = 'POR_REVISAR'", Integer.class))
				.isEqualTo(2);
	}

}
