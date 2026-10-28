package co.manaure.trekking.reserva;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Códigos PER-0001 y PAG-0001 a partir de las secuencias de la base (diseno §3.5). Pasado el 9999 el número
 * crece en dígitos. Un rollback deja huecos; se acepta.
 */
@Service
public class GeneradorCodigos {

	@PersistenceContext
	private EntityManager em;

	@Transactional
	public String siguienteCodigoInscripcion() {
		return formatear("PER", siguiente("inscripcion_codigo_seq"));
	}

	@Transactional
	public String siguienteCodigoPago() {
		return formatear("PAG", siguiente("pago_codigo_seq"));
	}

	static String formatear(String prefijo, long numero) {
		return String.format("%s-%04d", prefijo, numero);
	}

	private long siguiente(String secuencia) {
		return ((Number) em.createNativeQuery("select nextval('" + secuencia + "')").getSingleResult()).longValue();
	}

}
