package co.manaure.trekking.regla.modelo;

/** Hotel previo al evento: llegada y salida AAAA-MM-DD (texto, se validan) y si suma al acompañante. */
public record HotelSolicitado(String llegada, String salida, boolean conAcompanante) {
}
