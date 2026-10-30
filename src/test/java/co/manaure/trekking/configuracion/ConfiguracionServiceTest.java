package co.manaure.trekking.configuracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.manaure.trekking.TestcontainersConfiguration;

/** HU-N01: configuración singleton (id = 1) y bloqueo pesimista reutilizable. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ConfiguracionServiceTest {

	@Autowired
	ConfiguracionService servicio;

	@Autowired
	PlatformTransactionManager tm;

	@Test
	void leeLaFilaInicial() {
		ConfiguracionEvento c = servicio.obtener();

		assertThat(c.getId()).isEqualTo((short) 1);
		assertThat(c.getFechaEventoInicio()).isEqualTo(LocalDate.of(2026, 11, 28));
		assertThat(c.getFechaCierre()).isEqualTo(LocalDate.of(2026, 11, 9));
		assertThat(c.getCuposCarrera()).isEqualTo(60);
		assertThat(c.getCuposParamo()).isEqualTo(30);
		assertThat(c.getPrecioTitular()).isEqualTo(350000L);
		assertThat(c.getValorApartado()).isEqualTo(50000L);
		assertThat(c.getKitCompleto()).isEqualTo(300000L);
		assertThat(c.getPrecioHotelNoche()).isEqualTo(45000L);
		assertThat(c.getVersionTerminos()).isEqualTo("TYC-2026-10-28");
	}

	@Test
	void elBloqueoExigeUnaTransaccionEnCurso() {
		assertThatThrownBy(() -> servicio.obtenerConBloqueo()).isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void unaSegundaTransaccionEsperaHastaQueLaPrimeraTermina() throws Exception {
		CountDownLatch tomado = new CountDownLatch(1);
		CountDownLatch liberar = new CountDownLatch(1);
		TransactionTemplate tx = new TransactionTemplate(tm);

		CompletableFuture<Void> primera = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
			assertThat(servicio.obtenerConBloqueo().getId()).isEqualTo((short) 1);
			tomado.countDown();
			try {
				liberar.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}));
		assertThat(tomado.await(10, TimeUnit.SECONDS)).isTrue();

		CompletableFuture<Short> segunda = CompletableFuture
				.supplyAsync(() -> tx.execute(s -> servicio.obtenerConBloqueo().getId()));
		Thread.sleep(700);
		assertThat(segunda).as("espera mientras la primera conserva el bloqueo").isNotDone();

		liberar.countDown();
		primera.get(10, TimeUnit.SECONDS);
		assertThat(segunda.get(10, TimeUnit.SECONDS)).isEqualTo((short) 1);
	}

}
