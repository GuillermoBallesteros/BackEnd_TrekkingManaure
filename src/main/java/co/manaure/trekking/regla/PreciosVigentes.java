package co.manaure.trekking.regla;

/** Precios vigentes en pesos enteros (los que se guardan como {@code *_aplicado}). */
public record PreciosVigentes(long titular, long apartado, long acompananteSinKit, long acompananteConKit,
		long cuatrimoto, long parapente, long hotelNoche) {
}
