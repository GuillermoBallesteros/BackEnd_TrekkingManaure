package co.manaure.trekking.regla.modelo;

import co.manaure.trekking.pago.RevisionPago;

/** Pago reducido a lo que usan el estado derivado y los cupos. */
public record PagoRegla(long monto, RevisionPago revision) {
}
