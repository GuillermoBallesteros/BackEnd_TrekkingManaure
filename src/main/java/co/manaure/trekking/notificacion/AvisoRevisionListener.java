package co.manaure.trekking.notificacion;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import co.manaure.trekking.pago.PagoRevisadoEvent;

/**
 * Aviso inmediato de aprobación o rechazo (RN-24). Solo después del commit: si la revisión se revierte, no sale
 * correo. El envío va a un hilo propio para que la respuesta del panel nunca espere a Brevo (con reintentos podría
 * tardar decenas de segundos). Un solo hilo conserva el orden de las revisiones de un mismo pago.
 */
@Component
@ConditionalOnProperty(name = "app.brevo.activo", havingValue = "true")
public class AvisoRevisionListener implements DisposableBean {

	private static final Logger log = LoggerFactory.getLogger(AvisoRevisionListener.class);

	private final ServicioAvisos avisos;
	private final ExecutorService hilo = Executors.newSingleThreadExecutor(tarea -> {
		Thread t = new Thread(tarea, "avisos-revision");
		t.setDaemon(true);
		return t;
	});

	public AvisoRevisionListener(ServicioAvisos avisos) {
		this.avisos = avisos;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void alRevisarPago(PagoRevisadoEvent evento) {
		try {
			hilo.execute(() -> avisar(evento));
		}
		catch (RejectedExecutionException ex) {
			log.warn("Avisos: la API se está cerrando; no se avisó la revisión del pago {}.", evento.codigoPago());
		}
	}

	private void avisar(PagoRevisadoEvent evento) {
		try {
			avisos.avisarRevision(evento.codigoPago(), evento.revision());
		}
		catch (RuntimeException ex) {
			log.warn("Avisos: no se pudo avisar la revisión del pago {} ({}).", evento.codigoPago(),
					ex.getClass().getSimpleName());
		}
	}

	/** Al cerrar la API espera hasta 30 s a que salgan los avisos ya encolados. */
	@Override
	public void destroy() throws InterruptedException {
		hilo.shutdown();
		hilo.awaitTermination(30, TimeUnit.SECONDS);
	}

}
